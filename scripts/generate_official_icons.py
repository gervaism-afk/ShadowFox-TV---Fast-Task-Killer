"""Package the official artwork without redrawing or dropping transparency."""
from pathlib import Path
import sys
from PIL import Image

res = Path(sys.argv[1])
pack = Path(sys.argv[2])
pack.mkdir(parents=True, exist_ok=True)
icon = Image.open(res / 'drawable-nodpi/ultra_cleaner_icon_master.png').convert('RGBA')
banner = Image.open(res / 'drawable-nodpi/ultra_cleaner_banner_master.png').convert('RGBA')
for name, image in [('Ultra-Cleaner-MASTER.png', icon), ('Ultra-Cleaner-TV-Banner-MASTER.png', banner)]:
    assert image.getextrema()[3][0] == 0, 'Artwork must have real transparency'
    image.save(pack / name)
for size in [48, 72, 96, 144, 192, 256, 512, 1024]:
    icon.resize((size, size), Image.Resampling.LANCZOS).save(pack / f'Ultra-Cleaner-icon-{size}.png')
for density, size, width in [('mdpi',48,320),('hdpi',72,480),('xhdpi',96,640),('xxhdpi',144,960),('xxxhdpi',192,1280)]:
    folder = res / f'mipmap-{density}'
    folder.mkdir(exist_ok=True)
    image = icon.resize((size,size), Image.Resampling.LANCZOS)
    image.save(folder / 'ic_launcher.png')
    image.save(folder / 'ic_launcher_round.png')
    folder = res / f'drawable-{density}'
    folder.mkdir(exist_ok=True)
    banner.resize((width,width*9//16), Image.Resampling.LANCZOS).save(folder / 'ultra_cleaner_tv_banner.png')
for width in [320,640,1280]:
    banner.resize((width,width*9//16), Image.Resampling.LANCZOS).save(pack / f'Ultra-Cleaner-TV-Banner-{width}.png')
folder = res / 'mipmap-anydpi-v26'
folder.mkdir(exist_ok=True)
xml = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent" />
    <foreground><inset android:inset="16%" android:drawable="@drawable/ultra_cleaner_icon_master" /></foreground>
</adaptive-icon>\n'''
for name in ['ic_launcher.xml','ic_launcher_round.xml']:
    (folder/name).write_text(xml)
(pack/'README.txt').write_text('ShadowFox Ultra Cleaner official transparent artwork.\nSquare icons: Android phones/tablets and installer.\n16:9 banners: Android TV, Fire TV and Projectivy.\nAll sizes derive from the included masters.\n')
print('Official Ultra Cleaner launcher resources and full icon pack generated.')
