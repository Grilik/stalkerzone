"""Достаёт скелеты (кости без кубов) из стандартного пака TaCZ в skeletons.json, чтобы свои модели
работали со стандартными анимациями. Пак берётся из jar TaCZ: python3 extract_skeleton.py <распакованный tacz_default_gun> имя..."""
import json, re, sys, os
HERE = os.path.dirname(__file__)


def load(p):
    t = open(p, encoding='utf-8').read()
    t = re.sub(r'//[^\n]*', '', t)
    return json.loads(t)


def summary(geo):
    """Кости с кубами: пивот и габариты кубов (для подгонки своих деталей)."""
    out = []
    for b in geo['bones']:
        cs = b.get('cubes', [])
        if not cs:
            continue
        mn = [1e9] * 3; mx = [-1e9] * 3
        for c in cs:
            o, s = c['origin'], c['size']
            for i in range(3):
                mn[i] = min(mn[i], o[i]); mx[i] = max(mx[i], o[i] + s[i])
        out.append((b['name'], b.get('parent'), [round(v, 2) for v in b['pivot']], [round(v, 1) for v in mn], [round(v, 1) for v in mx], len(cs)))
    return out


if __name__ == '__main__':
    root = sys.argv[1]
    sk = json.load(open(os.path.join(HERE, 'skeletons.json')))
    for name in sys.argv[2:]:
        g = load(os.path.join(root, 'assets', 'tacz', 'geo_models', 'gun', name + '_geo.json'))
        geo = g['minecraft:geometry'][0]
        d = {k: v for k, v in geo['description'].items() if k.startswith('visible')}
        bones = []
        for b in geo['bones']:
            e = {'name': b['name']}
            if 'parent' in b: e['parent'] = b['parent']
            e['pivot'] = b['pivot']
            if 'rotation' in b: e['rotation'] = b['rotation']
            # кубы-метки рук и позиций оставляем: по ним TaCZ ставит руки и прицел
            if b['name'].endswith('_pos') or b['name'] in ('lefthand_pos', 'righthand_pos'):
                pass
            bones.append(e)
        sk[name] = {'desc': d, 'bones': bones}
        print('==', name)
        for row in summary(geo):
            print('  ', row)
    json.dump(sk, open(os.path.join(HERE, 'skeletons.json'), 'w'), ensure_ascii=False, indent=0)
