"""Минимальный RCON-клиент для проверки сервера (П-13): python3 -I tools/ci/rcon.py "forge tps" "stop".

Порт и пароль — RCON_PORT (25575) и RCON_PASSWORD (sz), как в server.properties, который пишет server_smoke.sh.
"""
import os
import socket
import struct
import sys


class Rcon:
    def __init__(self, port=None, password=None, timeout=120):
        port = int(port or os.environ.get("RCON_PORT", 25575))
        self.s = socket.create_connection(("127.0.0.1", port), timeout=timeout)
        self.i = 0
        self._send(3, password or os.environ.get("RCON_PASSWORD", "sz"))

    def _read(self, n):
        b = b""
        while len(b) < n:
            part = self.s.recv(n - len(b))
            if not part:
                raise ConnectionError("RCON: соединение закрыто")
            b += part
        return b

    def _send(self, kind, body):
        self.i += 1
        data = struct.pack("<ii", self.i, kind) + body.encode() + b"\0\0"
        self.s.sendall(struct.pack("<i", len(data)) + data)
        n = struct.unpack("<i", self._read(4))[0]
        return self._read(n)[8:-2].decode("utf-8", "replace")

    def cmd(self, c):
        return self._send(2, c)


if __name__ == "__main__":
    r = Rcon()
    for c in sys.argv[1:]:
        print(c, "->", r.cmd(c))
