import hashlib, io, json, sys, unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import dropbox_inbox_acquire as a

class Response:
    def __init__(self,body=None,content=b"",code=200,url="https://upload.wikimedia.org/test.png"):
        self.obj=body or {};self.content=content;self.status_code=code
        self.ok=200<=code<300;self.url=url;self.headers={}
        self.text=json.dumps(self.obj)
    def json(self):return self.obj
    def raise_for_status(self):
        if not self.ok:raise RuntimeError("HTTP "+str(self.status_code))
    def iter_content(self,chunk_size):yield self.content
    def __enter__(self):return self
    def __exit__(self,*args):return False

class FakeHTTP:
    def __init__(self,license="CC BY-SA 4.0",description="Own work"):
        f=io.BytesIO()
        Image.new("RGB",(600,800),"white").save(f,format="PNG")
        self.img=f.getvalue();self.license=license;self.description=description
        self.saved={};self.image_gets=0;self.calls=[]
    def get(self,url,**kwargs):
        self.calls.append(("get",url))
        if url==a.API:
            return Response({"query":{"pages":[{"imageinfo":[{
              "url":"https://upload.wikimedia.org/test.png","mime":"image/png",
              "size":len(self.img),"width":600,"height":800,
              "extmetadata":{
                 "LicenseShortName":{"value":self.license},
                 "ImageDescription":{"value":self.description},
                 "Artist":{"value":"Uploader"}}}]}]}})
        self.image_gets+=1
        return Response(content=self.img)
    def post(self,url,**kwargs):
        self.calls.append(("post",url))
        if url.endswith("/files/create_folder_v2"):return Response()
        if url.endswith("/files/upload"):
            path=json.loads(kwargs["headers"]["Dropbox-API-Arg"])["path"]
            self.saved[path]=kwargs["data"]
            return Response()
        if url.endswith("/files/download"):
            path=json.loads(kwargs["headers"]["Dropbox-API-Arg"])["path"]
            return Response(content=self.saved[path])
        raise AssertionError(url)

class InboxTests(unittest.TestCase):
    row={"source_id":"test","provider":"wikimedia_commons",
         "model":"GMT-Master-II-126713GRNR","file_title":"File:Example.png"}
    def test_success_original_and_readback(self):
        s=FakeHTTP()
        result=a.process(s,"TOKEN",self.row,False)
        self.assertEqual(result["status"],"uploaded_and_readback_verified")
        self.assertEqual(s.saved[result["image"]],s.img)
        self.assertEqual(result["sha256"],hashlib.sha256(s.img).hexdigest())
        meta=json.loads(s.saved[result["provenance"]])
        self.assertEqual(meta["status"],"UNREVIEWED_NOT_CALIBRATION_APPROVED")
        self.assertEqual(meta["physical_watch_id"],"UNKNOWN")
        self.assertTrue(meta["dropbox_readback"]["matched"])
    def test_prohibited_license_never_downloaded(self):
        s=FakeHTTP(license="CC BY-NC 4.0")
        with self.assertRaisesRegex(a.Blocked,"rights_not_permitted"):
            a.process(s,"TOKEN",self.row,False)
        self.assertEqual(s.image_gets,0)
        self.assertEqual(s.saved,{})
    def test_replica_never_downloaded(self):
        s=FakeHTTP(description="This is a replica Rolex watch")
        with self.assertRaisesRegex(a.Blocked,"documented_replica"):
            a.process(s,"TOKEN",self.row,False)
        self.assertEqual(s.image_gets,0)
    def test_dry_run_only_metadata(self):
        s=FakeHTTP()
        r=a.process(s,"",self.row,True)
        self.assertEqual(r["status"],"rights_metadata_checked")
        self.assertEqual(s.image_gets,0)
        self.assertEqual(s.saved,{})
    def test_no_credentials(self):
        with self.assertRaisesRegex(a.Blocked,"missing_dropbox_oauth_secrets"):
            a.token(FakeHTTP(),{})
    def test_no_unsupported_provider(self):
        s=FakeHTTP()
        with self.assertRaisesRegex(a.Blocked,"source_not_approved"):
            a.process(s,"TOKEN",{**self.row,"provider":"dealer"},False)
        self.assertEqual(s.calls,[])
    def test_no_calibration_folder(self):
        s=FakeHTTP()
        with self.assertRaisesRegex(a.Blocked,"unsafe_destination"):
            a.mkdir(s,"TOKEN","/Watch Align/Suitable Photos")

if __name__=="__main__":unittest.main()
