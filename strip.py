import pathlib
import re

def strip_comments(code):
    pattern = r'(""".*?"""|''.*?'')|(/\*.*?\*/|//[^\r\n]*$)'
    regex = re.compile(pattern, re.MULTILINE | re.DOTALL)
    def _replacer(match):
        if match.group(2) is not None:
            return ""
        else:
            return match.group(1)
    return regex.sub(_replacer, code)

src_dir = pathlib.Path('c:/Users/home/Documents/antigravity/lucid-euclid/app/src/main/java')
for p in src_dir.rglob('*.kt'):
    try:
        content = p.read_text(encoding='utf-8')
        stripped = strip_comments(content)
        stripped = re.sub(r'\n\s*\n', '\n', stripped)
        p.write_text(stripped, encoding='utf-8')
    except Exception as e:
        print(f"Error processing {p}: {e}")
