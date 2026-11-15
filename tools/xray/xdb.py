"""Архивы X-Ray .db (формат 2947/EE и Anomaly) на чистом Python — замена xdb.cpp там, где нет компилятора C++.

Оглавление — чанк 1 (бит 0x80000000 — сжато LZHuf, вариант Окумуры, как xrCore/LzHuf.cpp); файлы — как есть
или LZO1X (если размер в архиве ≠ настоящему).

  python -I xdb.py <архив.db> list [подстрока]
  python -I xdb.py <архив.db> extract <папка> [подстрока]
Из Python: Db(path).files() -> [(имя, real, comp, ptr)], Db(path).read(имя) -> bytes
"""
import os, struct, sys

# ---------------- LZHuf (Окумура: N=4096, F=60, THRESHOLD=2) ----------------
N, F, TH = 4096, 60, 2
N_CHAR = 256 - TH + F
T = N_CHAR * 2 - 1
R = T - 1
MAX_FREQ = 0x4000  # в xrCore 0x4000 (у Окумуры 0x8000) — проверено на оглавлении configs.db0

D_CODE = bytes([0] * 32 + [1] * 16 + [2] * 16 + [3] * 16 + [4] * 8 + [5] * 8 + [6] * 8 + [7] * 8 + [8] * 8 +
               [9] * 8 + [10] * 8 + [11] * 8 + [12] * 4 + [13] * 4 + [14] * 4 + [15] * 4 + [16] * 4 + [17] * 4 +
               [18] * 4 + [19] * 4 + [20] * 4 + [21] * 4 + [22] * 4 + [23] * 4 +
               [x for i in range(24, 48) for x in [i] * 2] + list(range(48, 64)))
D_LEN = bytes([3] * 32 + [4] * 48 + [5] * 64 + [6] * 48 + [7] * 48 + [8] * 16)


def lzhuf_decode(src, size=None):
    """Окумура LZHUF. size=None — первые 4 байта src: длина результата (как в xrCore)."""
    pos = 0
    if size is None:
        size = struct.unpack_from("<I", src)[0]
        pos = 4
    freq = [0] * (T + 1)
    prnt = [0] * (T + N_CHAR)
    son = [0] * T
    for i in range(N_CHAR):
        freq[i] = 1
        son[i] = i + T
        prnt[i + T] = i
    i, j = 0, N_CHAR
    while j <= R:
        freq[j] = freq[i] + freq[i + 1]
        son[j] = i
        prnt[i] = prnt[i + 1] = j
        i += 2
        j += 1
    freq[T] = 0xFFFF
    prnt[R] = 0

    bitbuf, bitlen = 0, 0
    data = src

    def getbit():
        nonlocal bitbuf, bitlen, pos
        while bitlen <= 8:
            b = data[pos] if pos < len(data) else 0
            pos += 1
            bitbuf |= b << (8 - bitlen)
            bitlen += 8
        i = bitbuf
        bitbuf = (bitbuf << 1) & 0xFFFF
        bitlen -= 1
        return (i >> 15) & 1

    def getbyte():
        nonlocal bitbuf, bitlen, pos
        while bitlen <= 8:
            b = data[pos] if pos < len(data) else 0
            pos += 1
            bitbuf |= b << (8 - bitlen)
            bitlen += 8
        i = bitbuf
        bitbuf = (bitbuf << 8) & 0xFFFF
        bitlen -= 8
        return (i >> 8) & 0xFF

    def reconst():
        j = 0
        for i in range(T):
            if son[i] >= T:
                freq[j] = (freq[i] + 1) // 2
                son[j] = son[i]
                j += 1
        i, j = 0, N_CHAR
        while j < T:
            k = i + 1
            f = freq[j] = freq[i] + freq[k]
            k = j - 1
            while f < freq[k]:
                k -= 1
            k += 1
            freq[k + 1:j + 1] = freq[k:j]
            freq[k] = f
            son[k + 1:j + 1] = son[k:j]
            son[k] = i
            i += 2
            j += 1
        for i in range(T):
            k = son[i]
            if k >= T:
                prnt[k] = i
            else:
                prnt[k] = prnt[k + 1] = i

    def update(c):
        if freq[R] == MAX_FREQ:
            reconst()
        c = prnt[c + T]
        while True:
            freq[c] += 1
            k = freq[c]
            l = c + 1
            if k > freq[l]:
                while k > freq[l + 1]:
                    l += 1
                freq[c] = freq[l]
                freq[l] = k
                i = son[c]
                prnt[i] = l
                if i < T:
                    prnt[i + 1] = l
                j = son[l]
                son[l] = i
                prnt[j] = c
                if j < T:
                    prnt[j + 1] = c
                son[c] = j
                c = l
            c = prnt[c]
            if c == 0:
                break

    def decode_char():
        c = son[R]
        while c < T:
            c = son[c + getbit()]
        c -= T
        update(c)
        return c

    def decode_position():
        i = getbyte()
        c = D_CODE[i] << 6
        j = D_LEN[i] - 2
        while j:
            i = (i << 1) + getbit()
            j -= 1
        return c | (i & 0x3F)

    text = bytearray(b" " * (N + F - 1))
    r = N - F
    out = bytearray()
    while len(out) < size:
        c = decode_char()
        if c < 256:
            out.append(c)
            text[r] = c
            r = (r + 1) & (N - 1)
        else:
            i = (r - decode_position() - 1) & (N - 1)
            j = c - 255 + TH
            for k in range(j):
                c2 = text[(i + k) & (N - 1)]
                out.append(c2)
                text[r] = c2
                r = (r + 1) & (N - 1)
    return bytes(out[:size])


# ---------------- LZO1X ----------------
def lzo1x_decode(src, out_len):
    out = bytearray()
    ip = 0
    state = 0

    def copy_match(m_pos, n):
        if len(out) - m_pos >= n:
            out.extend(out[m_pos:m_pos + n])
        else:
            for k in range(n):
                out.append(out[m_pos + k])

    t = src[ip]
    if t > 17:
        ip += 1
        t -= 17
        out += src[ip:ip + t]
        ip += t
        state = t if t < 4 else 4
    while True:
        t = src[ip]
        ip += 1
        if t < 16:
            if state == 0:
                if t == 0:
                    while src[ip] == 0:
                        t += 255
                        ip += 1
                    t += 15 + src[ip]
                    ip += 1
                t += 3
                out += src[ip:ip + t]
                ip += t
                state = 4
                continue
            if state != 4:
                m_pos = len(out) - 1 - (t >> 2) - (src[ip] << 2)
                ip += 1
                copy_match(m_pos, 2)
            else:
                m_pos = len(out) - (1 + 0x0800) - (t >> 2) - (src[ip] << 2)
                ip += 1
                copy_match(m_pos, 3)
            nxt = t & 3
        elif t >= 64:
            m_pos = len(out) - 1 - ((t >> 2) & 7) - (src[ip] << 3)
            ip += 1
            copy_match(m_pos, (t >> 5) + 1)
            nxt = t & 3
        elif t >= 32:
            n = t & 31
            if n == 0:
                while src[ip] == 0:
                    n += 255
                    ip += 1
                n += 31 + src[ip]
                ip += 1
            d = src[ip] | (src[ip + 1] << 8)
            ip += 2
            copy_match(len(out) - 1 - (d >> 2), n + 2)
            nxt = d & 3
        else:
            n = t & 7
            if n == 0:
                while src[ip] == 0:
                    n += 255
                    ip += 1
                n += 7 + src[ip]
                ip += 1
            d = src[ip] | (src[ip + 1] << 8)
            ip += 2
            m_pos = len(out) - ((t & 8) << 11) - (d >> 2)
            if m_pos == len(out):
                break  # конец потока
            copy_match(m_pos - 0x4000, n + 2)
            nxt = d & 3
        out += src[ip:ip + nxt]
        ip += nxt
        state = nxt
    if len(out) != out_len:
        raise ValueError("LZO: %d вместо %d байт" % (len(out), out_len))
    return bytes(out)


# ---------------- архив ----------------
class Db:
    def __init__(self, path):
        self.path = path
        total = os.path.getsize(path)
        table = None
        with open(path, "rb") as f:
            while f.tell() < total:
                h = f.read(8)
                if len(h) < 8:
                    break
                cid, sz = struct.unpack("<II", h)
                if (cid & 0x7FFFFFFF) == 1:
                    raw = f.read(sz)
                    table = lzhuf_decode(raw) if cid & 0x80000000 else raw
                    break
                f.seek(sz, 1)
        if table is None:
            raise ValueError("нет оглавления в " + path)
        self.entries = {}
        p = 0
        while p + 18 <= len(table):
            hs, real, comp, crc = struct.unpack_from("<HIII", table, p)
            nl = hs - 16
            name = table[p + 14:p + 14 + nl].decode("cp1251").replace("\\", "/")
            ptr = struct.unpack_from("<I", table, p + 14 + nl)[0]
            p += 14 + nl + 4
            self.entries[name] = (real, comp, ptr)

    def files(self):
        return [(n, *v) for n, v in self.entries.items()]

    def read(self, name):
        real, comp, ptr = self.entries[name]
        with open(self.path, "rb") as f:
            f.seek(ptr)
            c = f.read(comp)
        return c if real == comp else lzo1x_decode(c, real)


def main():
    db = Db(sys.argv[1])
    if sys.argv[2] == "list":
        mask = sys.argv[3] if len(sys.argv) > 3 else ""
        for n, real, comp, ptr in db.files():
            if mask in n:
                print("%10d %10d %10d %s" % (real, comp, ptr, n))
    else:
        out, mask = sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else ""
        k = 0
        for n, real, comp, ptr in db.files():
            if mask not in n or (real == 0 and comp == 0):
                continue
            path = os.path.join(out, n)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "wb").write(db.read(n))
            k += 1
        print(k, "файлов", file=sys.stderr)


if __name__ == "__main__":
    main()
