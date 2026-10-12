import copy,csv,json,math,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from calibrate import area,geometry_vector,reconstruct,references,estimate,centroid
from prepare import run,ROOT
class CalibrationChecks(unittest.TestCase):
 def test_corresponding_quadrilateral_vertices_do_not_force_parallel_sides(self):
  p=[[-.03,-.1],[.03,-.1],[.02,.1],[-.018,.1]]
  for shift in range(4):
   q=p[shift:]+p[:shift]
   self.assertAlmostEqual(area(reconstruct('baton',geometry_vector('baton',q))),area(p))
   self.assertEqual(geometry_vector('baton',p),geometry_vector('baton',q))
 def test_circle_free_centre_radius(self):
  p=reconstruct('round',[.01,-.02,.045]);v=geometry_vector('round',p)
  for a,b in zip(v,[.01,-.02,.045]):self.assertAlmostEqual(a,b)
 def test_heldout_and_replica_never_contribute(self):
  raw=[];meta={};triangle=[[0,.08],[-.05,-.06],[.05,-.06]]
  for i in range(5):
   id=str(i);meta[id]=dict(primary_eligible='true',primary_photo='true',split='development' if i<3 else 'heldout',group_id=id,family='gmt_126710',cv_fold=i%2,dealer='test')
   p=triangle if i<3 else [[10,10],[20,30],[40,10]]
   raw.append(dict(photo_id=id,markers=[dict(hour=12,shape='triangle',fit={'local_ideal':p},local_observed=None)]))
  ref,_=references(raw,meta);p=ref['models']['gmt_126710']['positions']['12'];self.assertEqual(p['geometric_groups'],3);self.assertAlmostEqual(area(p['geometric_polygon']),area(triangle));self.assertIsNone(p['observed_polygon'])
  changed=copy.deepcopy(raw);changed[3]['markers'][0]['fit']['local_ideal']=[[200,200],[400,200],[300,500]]
  ref2,_=references(changed,meta);self.assertEqual(ref,ref2)
 def test_multiple_photos_do_not_give_one_watch_extra_weight(self):
  entries=[]
  for group,x,n in [('a',0,20),('b',.001,1),('c',.002,1)]:
   for i in range(n):entries.append(({'group_id':group},{'fit':{'local_ideal':reconstruct('round',[x,0,.05])}}))
  p,n,_,_=estimate(entries,'round','geometric');self.assertEqual(n,3);self.assertAlmostEqual(centroid(p)[0],.001)
 def test_fewer_than_three_groups_withheld(self):
  entries=[({'group_id':str(i)},{'fit':{'local_ideal':reconstruct('round',[0,0,.05])}}) for i in range(2)]
  p,n,_,_=estimate(entries,'round','geometric');self.assertIsNone(p);self.assertEqual(n,2)
 def test_curated_identity_split_is_disjoint_and_quarantines_conflicts(self):
  with tempfile.TemporaryDirectory() as t:
   work=Path(t);rows=list(csv.DictReader((ROOT/'tools/luminous/curated_reviewed_provenance.csv').open()))
   for r in rows:r['path']='unused_original.jpg'
   with (work/'verified_inputs.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
   run(work,processing=False);out=list(csv.DictReader((work/'split_manifest.csv').open()));dev={r['group_id'] for r in out if r['split']=='development'};held={r['group_id'] for r in out if r['split']=='heldout'}
   self.assertFalse(dev&held)
   for id in ['b0a1a6ff0bf6db97','7888550a54853ebb']:self.assertEqual(next(r for r in out if r['photo_id']==id)['split'],'identity_quarantine')
   summary=json.loads((work/'selection.json').read_text());self.assertEqual(summary['recorded_watch_groups'],130);self.assertEqual(summary['conservative_identity_clusters'],128);self.assertEqual(summary['eligible_clusters'],123)
if __name__=='__main__':unittest.main()
