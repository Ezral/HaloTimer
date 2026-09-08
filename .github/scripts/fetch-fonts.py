"""Fetch the unmodified, licensed Poppins fonts from a pinned upstream revision."""
import hashlib
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
REVISION = "5e35378e6bda803962ee6fd257e444a7d459660d"
FILES = [
  {
    "name": "OFL.txt",
    "sha": "76df3b565672e3248a5715339d092d4cb6c75019"
  },
  {
    "name": "Poppins-Bold.ttf",
    "sha": "1982f38ab21303459aa1155265052ca599fa58d1"
  },
  {
    "name": "Poppins-Light.ttf",
    "sha": "931ae8fdc6a32eaa6666f31343f5cd37e74954a4"
  },
  {
    "name": "Poppins-Medium.ttf",
    "sha": "a590f5c3e4902a7cb10f4bbc5da0e65e667f7950"
  },
  {
    "name": "Poppins-Regular.ttf",
    "sha": "0bda228ade88b0bb5aac7da2c881d0c3f64d0817"
  },
  {
    "name": "Poppins-SemiBold.ttf",
    "sha": "c30ad104723a0e6e00e54768626cb02c5fdf6aee"
  }
]
for item in FILES:
    name = item["name"]
    destination = ROOT / ("app/src/main/assets/licenses/Poppins-OFL.txt" if name == "OFL.txt" else
                          "app/src/main/res/font/" + name.replace("Poppins-", "poppins_").lower())
    if destination.exists():
        data = destination.read_bytes()
    else:
        with urllib.request.urlopen(f"https://raw.githubusercontent.com/google/fonts/{REVISION}/ofl/poppins/{name}", timeout=60) as response:
            data = response.read()
    blob_sha = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if blob_sha != item["sha"]:
        raise RuntimeError(f"Unexpected font contents: {name}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)

# Fixed-width countdown numerals; keep the upstream font and its license unmodified.
for name, relative, expected in [
    ('fonts/ttf/JetBrainsMono-Regular.ttf', 'app/src/main/res/font/jetbrains_mono_regular.ttf', '711830ede02a366f8b99f88e52f3148405e67eaf'),
    ('OFL.txt', 'app/src/main/assets/licenses/JetBrainsMono-OFL.txt', '5ceee0025da23d2cd5fd14b28569708a8db4db2b'),
]:
    destination = ROOT / relative
    if destination.exists():
        data = destination.read_bytes()
    else:
        with urllib.request.urlopen('https://raw.githubusercontent.com/JetBrains/JetBrainsMono/19371302b95d218af43299bce79ddbddd0bc364d/' + name, timeout=60) as response:
            data = response.read()
    if hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() != expected:
        raise RuntimeError('Unexpected JetBrains Mono contents: ' + name)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
