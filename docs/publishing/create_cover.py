from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
S = 4
im = Image.new('RGB', (400*S, 400*S), '#101c29')
d = ImageDraw.Draw(im)

def poly(points, fill, outline=None, width=1):
    d.polygon([(int(x*S), int(y*S)) for x,y in points], fill=fill)
    if outline:
        d.line([(int(x*S), int(y*S)) for x,y in points + [points[0]]], fill=outline, width=width*S, joint='curve')

def line(points, fill, width=1):
    d.line([(int(x*S), int(y*S)) for x,y in points], fill=fill, width=width*S, joint='curve')

# A quiet spatial grid frames the collision volumes.
for x in range(-300, 701, 40):
    line([(x, 300), (x+240, 180)], '#1b2e3e')
    line([(x, 180), (x+240, 300)], '#1b2e3e')
d.rounded_rectangle((15*S,15*S,385*S,385*S), radius=25*S, outline='#2a4254', width=2*S)

def cube(cx, cy, w, h, colors):
    a=(cx,cy); b=(cx+w,cy+w/2); c=(cx,cy+w); e=(cx-w,cy+w/2)
    poly([a,b,c,e], colors[0])
    poly([e,c,(cx,cy+w+h),(cx-w,cy+w/2+h)], colors[1])
    poly([c,b,(cx+w,cy+w/2+h),(cx,cy+w+h)], colors[2])
    line([e,a,b,(cx+w,cy+w/2+h),(cx,cy+w+h),(cx-w,cy+w/2+h),e], '#b4ffee', 2)
    line([e,c,b], '#b4ffee', 2)
    line([c,(cx,cy+w+h)], '#b4ffee', 2)

# Offset entity volumes, with an amber impulse between them.
cube(137,80,62,99,('#82e9d1','#328f90','#256671'))
cube(268,113,56,91,('#9bf3d7','#43aaa1','#2a7f85'))
for yy, xx in [(125,46),(146,35),(167,47)]:
    line([(xx,yy),(xx+34,yy)], '#68dac4', 4)
poly([(216,73),(174,166),(204,166),(181,241),(250,139),(216,139),(239,73)], '#0f1c28', '#101c29', 9)
poly([(216,73),(174,166),(204,166),(181,241),(250,139),(216,139),(239,73)], '#ffce68')
line([(330,192),(347,192)], '#68dac4', 4)
line([(330,211),(358,211)], '#68dac4', 4)

font_path = 'C:/Windows/Fonts/arialbd.ttf'
font = ImageFont.truetype(font_path, 53*S)
text='ECOPT'
box=d.textbbox((0,0),text,font=font)
d.text(((400*S-(box[2]-box[0]))/2,282*S),text,font=font,fill='#eefcf6',stroke_width=0)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 11*S)
tag='ENTITY COLLISION OPTIMIZATION'
box=d.textbbox((0,0),tag,font=small)
d.text(((400*S-(box[2]-box[0]))/2,348*S),tag,font=small,fill='#98b7bd')
im.resize((400,400),Image.Resampling.LANCZOS).save(OUT/'ecopt-cover-400.png')
print(OUT/'ecopt-cover-400.png')
