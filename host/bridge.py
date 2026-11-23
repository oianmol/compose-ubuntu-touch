# Serves bands of the frame that the Kotlin renderer writes to a shared file, and relays input to/from it.
# File: width:int @8, height:int @12, nBands:int @16, bandRows:int @20, one Long seq per band @24, pixels from 4096.
import mmap, os, socket, struct, threading, time
import pyotherside

PATH = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "compose-host", "frame.bin")
HEADER = 4096
_map = None

def _open():
    global _map
    if _map is None and os.path.exists(PATH):
        _map = mmap.mmap(os.open(PATH, os.O_RDONLY), 0, access=mmap.ACCESS_READ)

def provider(image_id, requested_size):
    # image_id is "<band index>/<seq>"; the seq only makes the URL unique.
    idx = int(image_id.split("/")[0])
    w, h, n, br = struct.unpack_from("<iiii", _map, 8)
    rows = min(br, h - idx * br)
    off = HEADER + idx * br * w * 4
    return bytearray(_map[off:off + rows * w * 4]), (w, rows), pyotherside.format_rgb32

pyotherside.set_image_provider(provider)

def _watch():
    last = None
    while True:
        _open()
        if _map is not None:
            w, h, n, br = struct.unpack_from("<iiii", _map, 8)
            if n > 0 and last is None:
                last = [0] * n
                pyotherside.send("init", w, h, br, n)
            if last is not None:
                seqs = struct.unpack_from("<%dq" % n, _map, 24)
                changed = [[i, s] for i, (s, l) in enumerate(zip(seqs, last)) if s != l]
                if changed:
                    last = list(seqs)
                    pyotherside.send("bands", changed)
        time.sleep(0.004)

threading.Thread(target=_watch, daemon=True).start()

# Link to the Kotlin renderer over a Unix socket.
# Out: touches "<d|m|u> <id> <x> <y>", text "t <urlencoded> <cursor>", keyboard "kv <0|1> <heightPx>".
# In:  "show <urlencoded text> <cursor>" / "hide" (open or close Ubuntu Touch's keyboard).
from urllib.parse import quote, unquote

SOCK = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "compose-host", "input.sock")
_sock = None

def _send(line):
    global _sock
    s = _sock
    if s is None:
        return
    try:
        s.send((line + "\n").encode())
    except OSError:
        _sock = None

def touch(phase, i, x, y):
    _send("%s %d %.1f %.1f" % (phase, i, x, y))

def text(t, cursor):
    _send("t %s %d" % (quote(t, safe=""), cursor))

def enter():
    _send("e")

def kbstate(visible, height):
    _send("kv %d %d" % (1 if visible else 0, height))

def _handle(line):
    p = line.split(" ")
    if p[0] == "show" and len(p) == 3:
        pyotherside.send("kb", "show", unquote(p[1]), int(p[2]))
    elif p[0] == "hide":
        pyotherside.send("kb", "hide", "", 0)

def _link():
    global _sock
    while True:
        try:
            s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            s.connect(SOCK)
        except OSError:
            time.sleep(0.3)
            continue
        _sock = s
        buf = b""
        try:
            while True:
                data = s.recv(4096)
                if not data:
                    break
                buf += data
                while b"\n" in buf:
                    line, buf = buf.split(b"\n", 1)
                    _handle(line.decode())
        except OSError:
            pass
        _sock = None

threading.Thread(target=_link, daemon=True).start()
