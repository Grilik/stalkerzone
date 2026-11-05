"""Собрать чек-лист из docs/checklist/*.md одним текстом (П-06): введение, версии, карточки, задачи рабочих чатов.
python3 tools/checklist.py > CHECKLIST_ALL.md"""
import glob, os, re

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs', 'checklist')


def order(p):
    n = os.path.basename(p)
    if n.startswith('v'): return (0, n)
    if re.match(r'\d', n): return (1, n)
    return (2, n)


parts = [open(os.path.join(D, 'README.md'), encoding='utf-8').read().strip()]
for p in sorted((p for p in glob.glob(os.path.join(D, '*.md')) if not p.endswith('README.md')), key=order):
    t = open(p, encoding='utf-8').read().strip()
    parts.append('#' + t if t.startswith('# ') else t)   # заголовок файла → раздел ##
print('\n\n---\n\n'.join(parts))
