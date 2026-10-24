import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import bobs_authorized_bulk as mod

class FakeReply:
    url="https://www.bobswatches.com/used-role-126710blnr.html"
    text='''<html><body><h1>Rolex 126710BLNR 193749</h1>
      <img src="/images/Used-Rolex-GMT-126710-SKU193749.jpg"/>
      <img src="/images/Unrelated-SKU199999.jpg"/>
      <img data-large_image="https://www.bobswatches.com/images/Used-Rolex-GMT-126710-SKU193749-side.jpg"/>
      <a href="/rolex-watches.html">More watches</a>
      </body></html>'''
    def raise_for_status(self):pass

class FakeSession:
    def get(self,*args,**kw):return FakeReply()

class Tests(unittest.TestCase):
    def test_stock_specific_only(self):
        row={"listing_url":FakeReply.url,"stock":"193749","model":"126710BLNR"}
        urls=mod.images_on_listing(FakeSession(),row)
        self.assertEqual(len(urls),2)
        self.assertTrue(all("193749" in x for x in urls))
        self.assertTrue(all("/images/" in x for x in urls))
    def test_model_rejection(self):
        with self.assertRaisesRegex(mod.IntakeError,"exact_model_not_in_listing"):
            mod.images_on_listing(FakeSession(),{"listing_url":FakeReply.url,"stock":"193749","model":"124060"})
    def test_missing_stock_rejection(self):
        with self.assertRaisesRegex(mod.IntakeError,"stock_number_absent"):
            mod.images_on_listing(FakeSession(),{"listing_url":FakeReply.url,"stock":"111111","model":"126710BLNR"})
    def test_unrelated_and_http_disallowed(self):
        self.assertEqual(mod.stock_image("http://evil.com/images/SKU193749.jpg",FakeReply.url,"193749"),"")
        self.assertEqual(mod.stock_image("https://www.bobswatches.com/images/SKU193749.gif",FakeReply.url,"193749"),"")
        self.assertEqual(mod.stock_image("/images/SKU999999.jpg",FakeReply.url,"193749"),"")
    def test_filename_boundary(self):
        self.assertEqual(mod.stock_image("/images/SKU1193749.jpg",FakeReply.url,"193749"),"")
        self.assertNotEqual(mod.stock_image("/images/SKU193749-side.jpg",FakeReply.url,"193749"),"")
    def test_no_calibration_promotion(self):
        self.assertEqual(mod.ROOT,"/Watch Align/Incoming Photos")
if __name__=="__main__":unittest.main()
