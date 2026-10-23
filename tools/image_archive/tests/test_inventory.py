from hashlib import sha256
import csv
import io
import zipfile
from PIL import Image
from tools.image_archive.inventory import inventory, available_originals
from tools.image_archive.screening import Screener


def test_inventory_requires_real_bytes_and_finds_extensionless_zip_objects(tmp_path):
    data=io.BytesIO()
    Image.new('RGB',(100,100)).save(data,format='PNG')
    raw=data.getvalue(); digest=sha256(raw).hexdigest()
    folder=tmp_path/'tools/research'
    folder.mkdir(parents=True)
    (folder/'catalogue.csv').write_text('sha256,physical_watch_id,model\n'+digest+',watch1,124060\n'+digest+',watch1,124060\n'+'0'*64+',watch2,124060\n')
    with zipfile.ZipFile(tmp_path/'artifact.zip','w') as z:
        z.writestr('objects/sha256/'+digest,raw)
    result=inventory(tmp_path, screener=Screener())
    assert result['total']['rows']==3
    assert result['total']['unique_sha256']==2
    assert result['total']['available_unique_originals']==1
    assert result['total']['unique_hash_status']['missing']==1
    assert result['total']['suitable_watch_ids']==0
    assert not (tmp_path/'objects').exists()
