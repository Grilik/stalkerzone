#include <stddef.h>
#include <stdint.h>
#include <string.h>
/* LZO1X распаковка (по логике lzo1x_decompress_safe ядра Linux). 0 — успех. */
int lzo1x_dec(const uint8_t *in, size_t in_len, uint8_t *out, size_t *out_len) {
    const uint8_t *ip = in, *ip_end = in + in_len;
    uint8_t *op = out, *op_end = out + *out_len;
    const uint8_t *m_pos;
    size_t t, next = 0, state = 0;
#define NEED_IP(x) if ((size_t)(ip_end - ip) < (size_t)(x)) return -4
#define NEED_OP(x) if ((size_t)(op_end - op) < (size_t)(x)) return -5
    NEED_IP(1);
    if (*ip > 17) {
        t = *ip++ - 17;
        if (t < 4) { next = t; goto match_next; }
        goto copy_literal_run;
    }
    for (;;) {
        NEED_IP(1);
        t = *ip++;
        if (t < 16) {
            if (state == 0) {
                if (t == 0) {
                    const uint8_t *last = ip;
                    while (ip < ip_end && *ip == 0) ip++;
                    NEED_IP(1);
                    t += (size_t)(ip - last) * 255 + 15 + *ip++;
                }
                t += 3;
copy_literal_run:
                NEED_IP(t); NEED_OP(t);
                memcpy(op, ip, t); op += t; ip += t;
                state = 4;
                continue;
            } else if (state != 4) {
                next = t & 3;
                m_pos = op - 1 - (t >> 2);
                NEED_IP(1);
                m_pos -= *ip++ << 2;
                if (m_pos < out) return -6;
                NEED_OP(2);
                op[0] = m_pos[0]; op[1] = m_pos[1]; op += 2;
                goto match_next;
            } else {
                next = t & 3;
                m_pos = op - (1 + 0x0800) - (t >> 2);
                NEED_IP(1);
                m_pos -= *ip++ << 2;
                t = 3;
            }
        } else if (t >= 64) {
            next = t & 3;
            m_pos = op - 1 - ((t >> 2) & 7);
            NEED_IP(1);
            m_pos -= *ip++ << 3;
            t = (t >> 5) - 1 + 2;
        } else if (t >= 32) {
            t = (t & 31) + 2;
            if (t == 2) {
                const uint8_t *last = ip;
                while (ip < ip_end && *ip == 0) ip++;
                NEED_IP(1);
                t += (size_t)(ip - last) * 255 + 31 + *ip++;
            }
            NEED_IP(2);
            m_pos = op - 1;
            next = ip[0] | (ip[1] << 8); ip += 2;
            m_pos -= next >> 2;
            next &= 3;
        } else {
            m_pos = op - ((t & 8) << 11);
            t = (t & 7) + 2;
            if (t == 2) {
                const uint8_t *last = ip;
                while (ip < ip_end && *ip == 0) ip++;
                NEED_IP(1);
                t += (size_t)(ip - last) * 255 + 7 + *ip++;
            }
            NEED_IP(2);
            next = ip[0] | (ip[1] << 8); ip += 2;
            m_pos -= next >> 2;
            next &= 3;
            if (m_pos == op) goto eof_found;
            m_pos -= 0x4000;
        }
        if (m_pos < out) return -6;
        NEED_OP(t);
        { uint8_t *oe = op + t; op[0] = m_pos[0]; op[1] = m_pos[1]; op += 2; m_pos += 2; while (op < oe) *op++ = *m_pos++; }
match_next:
        state = next;
        t = next;
        if (t) { NEED_IP(t); NEED_OP(t); memcpy(op, ip, t); op += t; ip += t; }
    }
eof_found:
    *out_len = op - out;
    return 0;
}
