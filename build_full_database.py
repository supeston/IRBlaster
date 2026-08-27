import zipfile
import re
import json
import gzip
import os

def hex_to_int(s):
    parts = s.strip().split()
    if not parts:
        return 0
    val = 0
    for i, p in enumerate(parts):
        try:
            b = int(p, 16)
            val |= (b << (i * 8))
        except ValueError:
            pass
    return val

def encode_nec(addr_int, cmd_int):
    addr_lo = addr_int & 0xFF
    addr_hi = (addr_int >> 8) & 0xFF
    cmd_lo = cmd_int & 0xFF
    cmd_hi = (~cmd_lo) & 0xFF

    if addr_hi == 0:
        addr_hi = (~addr_lo) & 0xFF

    bits = []
    for i in range(8):
        bits.append((addr_lo >> i) & 1)
    for i in range(8):
        bits.append((addr_hi >> i) & 1)
    for i in range(8):
        bits.append((cmd_lo >> i) & 1)
    for i in range(8):
        bits.append((cmd_hi >> i) & 1)

    pattern = [9000, 4500]
    for b in bits:
        pattern.append(560)
        pattern.append(1690 if b == 1 else 560)
    pattern.append(560)
    return pattern

def encode_samsung32(addr_int, cmd_int):
    addr_lo = addr_int & 0xFF
    addr_hi = addr_lo
    cmd_lo = cmd_int & 0xFF
    cmd_hi = (~cmd_lo) & 0xFF

    bits = []
    for i in range(8):
        bits.append((addr_lo >> i) & 1)
    for i in range(8):
        bits.append((addr_hi >> i) & 1)
    for i in range(8):
        bits.append((cmd_lo >> i) & 1)
    for i in range(8):
        bits.append((cmd_hi >> i) & 1)

    pattern = [4500, 4500]
    for b in bits:
        pattern.append(560)
        pattern.append(1690 if b == 1 else 560)
    pattern.append(560)
    return pattern

def encode_sony(cmd_int, addr_int, num_bits=12):
    cmd = cmd_int & 0x7F
    addr = addr_int & 0x1FFF

    bits = []
    for i in range(7):
        bits.append((cmd >> i) & 1)
    addr_bits = num_bits - 7
    for i in range(addr_bits):
        bits.append((addr >> i) & 1)

    pattern = [2400, 600]
    for b in bits:
        pattern.append(1200 if b == 1 else 600)
        pattern.append(600)
    return pattern

def encode_rc5(addr_int, cmd_int):
    half_bits = [True, False, True, False, True, False]
    addr = addr_int & 0x1F
    cmd = cmd_int & 0x3F

    for i in range(4, -1, -1):
        b = (addr >> i) & 1
        if b == 1:
            half_bits.extend([False, True])
        else:
            half_bits.extend([True, False])

    for i in range(5, -1, -1):
        b = (cmd >> i) & 1
        if b == 1:
            half_bits.extend([False, True])
        else:
            half_bits.extend([True, False])

    pattern = []
    curr_level = True
    curr_dur = 0
    half_dur = 889
    start = 0
    while start < len(half_bits) and not half_bits[start]:
        start += 1

    for i in range(start, len(half_bits)):
        lvl = half_bits[i]
        if lvl == curr_level:
            curr_dur += half_dur
        else:
            pattern.append(curr_dur)
            curr_level = lvl
            curr_dur = half_dur
    if curr_dur > 0:
        pattern.append(curr_dur)
    return pattern

def encode_rc6(addr_int, cmd_int):
    pattern = [2666, 889]
    half_bits = [False, True, True, False, True, False, True, False]
    half_bits.extend([True, True, False, False])

    addr = addr_int & 0xFF
    cmd = cmd_int & 0xFF

    for i in range(7, -1, -1):
        b = (addr >> i) & 1
        if b == 1:
            half_bits.extend([False, True])
        else:
            half_bits.extend([True, False])

    for i in range(7, -1, -1):
        b = (cmd >> i) & 1
        if b == 1:
            half_bits.extend([False, True])
        else:
            half_bits.extend([True, False])

    curr_level = True
    curr_dur = 0
    half_dur = 444
    for lvl in half_bits:
        if lvl == curr_level:
            curr_dur += half_dur
        else:
            pattern.append(curr_dur)
            curr_level = lvl
            curr_dur = half_dur
    if curr_dur > 0:
        pattern.append(curr_dur)
    return pattern

def encode_kaseikyo(addr_int, cmd_int):
    pattern = [3456, 1728]
    for i in range(48):
        b = (cmd_int >> (i % 16)) & 1
        pattern.append(432)
        pattern.append(1296 if b == 1 else 432)
    pattern.append(432)
    return pattern

def encode_rca(addr_int, cmd_int):
    pattern = [4000, 4000]
    for i in range(8):
        b = (addr_int >> i) & 1
        pattern.append(500)
        pattern.append(2000 if b == 1 else 1000)
    for i in range(8):
        b = (cmd_int >> i) & 1
        pattern.append(500)
        pattern.append(2000 if b == 1 else 1000)
    pattern.append(500)
    return pattern

def convert_button_to_pattern(btn):
    b_type = btn.get('type', '')
    if b_type == 'raw':
        data_str = btn.get('data', '')
        freq = int(btn.get('frequency', 38000))
        pts = [int(x) for x in re.findall(r'\d+', data_str)]
        if pts and len(pts) >= 4:
            return freq, pts, 'RAW'
        return None

    if b_type == 'parsed':
        protocol = btn.get('protocol', '').upper()
        addr_str = btn.get('address', '00 00 00 00')
        cmd_str = btn.get('command', '00 00 00 00')
        addr_int = hex_to_int(addr_str)
        cmd_int = hex_to_int(cmd_str)

        if 'SAMSUNG' in protocol:
            return 38000, encode_samsung32(addr_int, cmd_int), 'SAMSUNG'
        elif 'NEC' in protocol:
            return 38000, encode_nec(addr_int, cmd_int), 'NEC'
        elif 'SIRC20' in protocol:
            return 40000, encode_sony(cmd_int, addr_int, 20), 'SONY_SIRC'
        elif 'SIRC15' in protocol:
            return 40000, encode_sony(cmd_int, addr_int, 15), 'SONY_SIRC'
        elif 'SIRC' in protocol or 'SONY' in protocol:
            return 40000, encode_sony(cmd_int, addr_int, 12), 'SONY_SIRC'
        elif 'RC5' in protocol:
            return 36000, encode_rc5(addr_int, cmd_int), 'RC5'
        elif 'RC6' in protocol:
            return 36000, encode_rc6(addr_int, cmd_int), 'RC6'
        elif 'KASEIKYO' in protocol or 'PANASONIC' in protocol:
            return 37000, encode_kaseikyo(addr_int, cmd_int), 'RAW'
        elif 'RCA' in protocol:
            return 38000, encode_rca(addr_int, cmd_int), 'RAW'
        else:
            return 38000, encode_nec(addr_int, cmd_int), 'NEC'

    return None

def is_power_button(name):
    n = name.lower().strip()
    return any(k in n for k in ['power', 'pwr', 'off', 'standby', 'power_off', 'power_toggle', 'shut'])

def parse_flipper_file(content):
    buttons = []
    current_btn = {}
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            if current_btn and 'name' in current_btn:
                buttons.append(current_btn)
                current_btn = {}
            continue
        if ':' in line:
            k, v = line.split(':', 1)
            current_btn[k.strip()] = v.strip()
    if current_btn and 'name' in current_btn:
        buttons.append(current_btn)
    return buttons

CATEGORY_MAP = {
    'TVs': 'TV',
    'Universal_TV_Remotes': 'TV',
    'ACs': 'AC',
    'Cable_Boxes': 'SET_TOP_BOX',
    'DVB-T': 'SET_TOP_BOX',
    'TV_Tuner': 'SET_TOP_BOX',
    'Streaming_Devices': 'SET_TOP_BOX',
    'Audio_and_Video_Receivers': 'AUDIO',
    'SoundBars': 'AUDIO',
    'Speakers': 'AUDIO',
    'CD_Players': 'AUDIO',
    'MiniDisc': 'AUDIO',
    'Projectors': 'PROJECTOR',
    'Fans': 'FAN_HEATER',
    'Heaters': 'FAN_HEATER',
    'Air_Purifiers': 'FAN_HEATER',
    'Humidifiers': 'FAN_HEATER',
    'LED_Lighting': 'LED',
    'Cameras': 'CAMERA'
}

print('Processing Flipper-IRDB...')

database = {
    'categories': {
        'TV': {},
        'AC': {},
        'SET_TOP_BOX': {},
        'AUDIO': {},
        'PROJECTOR': {},
        'FAN_HEATER': {},
        'LED': {},
        'CAMERA': {}
    },
    'global_codes': {
        'TV': [],
        'AC': [],
        'SET_TOP_BOX': [],
        'AUDIO': [],
        'PROJECTOR': [],
        'FAN_HEATER': [],
        'LED': [],
        'CAMERA': []
    }
}

seen_patterns = {cat: set() for cat in database['categories']}
total_power_codes = 0

with zipfile.ZipFile('flipper_irdb.zip', 'r') as z:
    for filename in z.namelist():
        if not filename.endswith('.ir'):
            continue
        parts = filename.split('/')
        if len(parts) < 3:
            continue
        repo_cat = parts[1]
        if repo_cat not in CATEGORY_MAP:
            continue

        app_cat = CATEGORY_MAP[repo_cat]
        brand = parts[2].replace('_', ' ').replace('-', ' ').strip()
        if not brand or brand.lower() == 'unfiled':
            brand = 'Universal'

        model_name = os.path.splitext(parts[-1])[0].replace('_', ' ')

        try:
            content = z.read(filename).decode('utf-8', errors='ignore')
            buttons = parse_flipper_file(content)
            for btn in buttons:
                b_name = btn.get('name', '')
                if is_power_button(b_name):
                    res = convert_button_to_pattern(btn)
                    if res:
                        freq, pattern, proto = res
                        sig = (freq, tuple(pattern[:16])) # fingerprint
                        
                        if brand not in database['categories'][app_cat]:
                            database['categories'][app_cat][brand] = []

                        code_item = {
                            'id': f"{app_cat}_{brand}_{len(database['categories'][app_cat][brand])}",
                            'brand': brand,
                            'model': f"{model_name} ({b_name})",
                            'category': app_cat,
                            'frequency': freq,
                            'protocol': proto,
                            'pattern': pattern
                        }
                        database['categories'][app_cat][brand].append(code_item)
                        total_power_codes += 1

                        if sig not in seen_patterns[app_cat]:
                            seen_patterns[app_cat].add(sig)
                            database['global_codes'][app_cat].append(code_item)
        except Exception as e:
            pass

print(f"Total power codes parsed from repo: {total_power_codes}")

# Add extensive CIS & Global Brand profiles if missing or under-represented
cis_brands = {
    'DEXP': [
        ('DEXP Smart TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('DEXP LED TV Variant A (NEC 0x807F)', 38000, encode_nec(0x807F, 0x12), 'NEC'),
        ('DEXP LED TV Variant B (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x08), 'NEC'),
        ('DEXP 4K Android TV (NEC 0x40BF)', 38000, encode_nec(0x40BF, 0x40), 'NEC'),
        ('DEXP Classic TV (NEC 0x00FF)', 38000, encode_nec(0x00FF, 0x48), 'NEC')
    ],
    'BBK': [
        ('BBK LED TV RC-53 (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('BBK Smart Android TV (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC'),
        ('BBK Classic TV RC-29 (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x12), 'NEC'),
        ('BBK Lem2200 (NEC 0x08F7)', 38000, encode_nec(0x08F7, 0x48), 'NEC'),
        ('BBK DVD/TV Combo (NEC 0x00FF)', 38000, encode_nec(0x00FF, 0x18), 'NEC')
    ],
    'Xiaomi': [
        ('Xiaomi Mi TV 4A/4S/P1/A2 (NEC 0x1FE0)', 38000, encode_nec(0x1FE0, 0x02), 'NEC'),
        ('Xiaomi Mi TV Box / Stick (NEC 0x1FE0)', 38000, encode_nec(0x1FE0, 0x1A), 'NEC'),
        ('Xiaomi Redmi Smart TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Xiaomi Laser Projector (NEC 0x1FE0)', 38000, encode_nec(0x1FE0, 0x48), 'NEC')
    ],
    'Haier': [
        ('Haier Smart Android TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Haier HTR-A18E / HTR-U27E (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x08), 'NEC'),
        ('Haier 4K UHD Series (NEC 0x807F)', 38000, encode_nec(0x807F, 0x12), 'NEC'),
        ('Haier Classic LCD (RC5 0x00)', 36000, encode_rc5(0x00, 0x0C), 'RC5')
    ],
    'Supra': [
        ('Supra Smart TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Supra LED TV STV-LC (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC'),
        ('Supra Classic TV (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x12), 'NEC')
    ],
    'Mystery': [
        ('Mystery MTV Series (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Mystery Smart TV (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC'),
        ('Mystery Classic TV (NEC 0x00FF)', 38000, encode_nec(0x00FF, 0x12), 'NEC')
    ],
    'Polar': [
        ('Polar / Polarline Smart TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Polar 32LTV / 43LTV (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x08), 'NEC')
    ],
    'Telefunken': [
        ('Telefunken Smart TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Telefunken TF-LED (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC'),
        ('Telefunken Classic (RC5 0x00)', 36000, encode_rc5(0x00, 0x0C), 'RC5')
    ],
    'Kivi': [
        ('Kivi Smart TV Series 7/8 (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Kivi Android TV 4K (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC')
    ],
    'Sber': [
        ('Sber Salute TV / SberBox (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Sber TV Line (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC')
    ],
    'Yasin': [
        ('Yasin Smart LED TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Yasin Android TV (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x08), 'NEC')
    ],
    'Harper': [
        ('Harper LED TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Harper Smart TV (NEC 0x807F)', 38000, encode_nec(0x807F, 0x08), 'NEC')
    ],
    'Starwind': [
        ('Starwind Smart TV (NEC 0x00BF)', 38000, encode_nec(0x00BF, 0x12), 'NEC'),
        ('Starwind LED TV (NEC 0x04FB)', 38000, encode_nec(0x04FB, 0x08), 'NEC')
    ]
}

for brand_name, code_list in cis_brands.items():
    if brand_name not in database['categories']['TV']:
        database['categories']['TV'][brand_name] = []
    for idx, (m_name, freq, pat, proto) in enumerate(code_list):
        c_item = {
            'id': f"TV_{brand_name}_{idx}",
            'brand': brand_name,
            'model': m_name,
            'category': 'TV',
            'frequency': freq,
            'protocol': proto,
            'pattern': pat
        }
        database['categories']['TV'][brand_name].append(c_item)
        sig = (freq, tuple(pat[:16]))
        if sig not in seen_patterns['TV']:
            seen_patterns['TV'].add(sig)
            database['global_codes']['TV'].append(c_item)

# Sort brands alphabetically in each category
for cat in database['categories']:
    sorted_dict = {k: database['categories'][cat][k] for k in sorted(database['categories'][cat].keys(), key=lambda s: s.lower())}
    database['categories'][cat] = sorted_dict

print(f"Final summary:")
for cat, brands in database['categories'].items():
    print(f"  Category {cat}: {len(brands)} brands, {len(database['global_codes'][cat])} distinct bruteforce codes")

# Write to assets
assets_dir = 'c:/Users/home/Documents/antigravity/lucid-euclid/app/src/main/assets'
os.makedirs(assets_dir, exist_ok=True)
json_path = os.path.join(assets_dir, 'flipper_database.json')

with open(json_path, 'w', encoding='utf-8') as f:
    json.dump(database, f, ensure_ascii=False)

print(f"Saved {json_path}: {os.path.getsize(json_path)} bytes")

gz_path = os.path.join(assets_dir, 'flipper_database.json.gz')
with gzip.open(gz_path, 'wt', encoding='utf-8') as f:
    json.dump(database, f, ensure_ascii=False)

print(f"Saved compressed {gz_path}: {os.path.getsize(gz_path)} bytes")
