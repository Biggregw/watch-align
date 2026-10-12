import csv,json,sys,shutil
from pathlib import Path
from PIL import Image,ImageDraw
work=Path(sys.argv[1]);out=Path(sys.argv[2]);rows=list(csv.DictReader(open(out/'recovered_markers.csv'))) if (out/'recovered_markers.csv').exists() else []
summary=json.loads((out/'comparison.json').read_text());text=['# Frozen luminous fit regression','', 'Same forty Alpha107 originals and frozen reference polygons. Every previous usable physical polygon and recorded outer value is asserted unchanged. No reference derivation, harvesting or acceptance thresholds. Listing identities remain provisional.','', '| Model | Before observed | After observed | Fitted geometry | Alignment only | Partial alignment supported | Newly measurable from previous withholding |','|---|---:|---:|---:|---:|---:|---:|']
for model,s in summary.items():text.append(f"| {model} | {s['before_assessed']} | {s['after_assessed']} | {s['fitted_assessed']} | {s['alignment_percentage_assessed']} | {s['partial_alignment_supported']} | {s['recovered']} |")
text+=['','Fitted geometry reconstructs independently determined sides/intersections or circle centre/radius. Alignment-only rigidly transforms the unchanged expected template. Observed physical footprint is separate. All three use the same filled expected area denominator and saved polygons/regions; percentages are never added. Unsupported dimensions and percentages remain withheld.','', '## Recovered comparison contact sheets','']
for model in summary:
 selected=[r for r in rows if r['model']==model];tiles=[]
 for r in selected:
  path=work/'gallery'/model/f"{r['photo_id']}_{r['hour']}.png"
  if path.exists():
   image=Image.open(path).convert('RGB');image.thumbnail((960,290));tiles.append(image)
   if len(tiles)<=3:shutil.copy(path,out/path.name)
 for start in range(0,len(tiles),8):
  batch=tiles[start:start+8];sheet=Image.new('RGB',(1920,290*((len(batch)+1)//2)),(0,0,0))
  for i,image in enumerate(batch):sheet.paste(image,((i%2)*960,(i//2)*290))
  name=f'{model}_recoveries_{start//8+1}.jpg';sheet.save(out/name,quality=94);text.append(f'![Raw / fitted / alignment-only / observed comparisons]({name})')
text+=['','Diagnostics distinguish seed rejection, crop leakage, enclosed dark pixels, convexity, contour instability and missing separator evidence. Enclosed pixels may be a separator shadow in a merged luminous/metal mask; they are not automatically material holes.','', '## Limitations and lessons learned','', 'Independent edge fits can support alignment without proving every physical corner. Fitted percentages include idealised extrapolation and may disagree with physical footprint percentages. Fit residual and subset stability are not complete photographic confidence intervals. Human inspection of recovered polygons is required; this report does not claim universal absence of false detections or manufacturing tolerances.']
(out/'README.md').write_text('\n'.join(text)+'\n')
