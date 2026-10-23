from dataclasses import asdict
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/dataset_harvester'))
from harvester.pipeline import Pipeline
from harvester.config import Paths
from harvester.state import SourceRecord, ImageRecord


def test_gmt_hook_preserves_image_and_source_records(tmp_path):
    class RecordingArchive:
        def __init__(self): self.calls=[]
        def put_verified(self,path,digest,association):
            self.calls.append((path,digest,association))
            return {'status':'inconclusive'}
    archive=RecordingArchive()
    # Avoid acquisition construction entirely; exercise its opt-in output bridge.
    pipeline=object.__new__(Pipeline)
    pipeline.archive=archive
    pipeline.archive_candidates={("source","abc")}
    pipeline.dry_run=False
    source=SourceRecord(key='source',url='https://example.test/listing',model='126710BLNR',image_shas=['abc'])
    image=ImageRecord(sha256='abc',seq=0,local_path=str(tmp_path/'original.jpg'),suitable=True)
    pipeline.state=type('State',(),{'sources':{'source':source},'images':{'abc':image}})()
    before=(asdict(source),asdict(image))
    assert pipeline.archive_images()==[{'status':'inconclusive'}]
    assert (asdict(source),asdict(image))==before
    assert pipeline.archive_images()==[]  # Never migrate historical state implicitly.
    assert archive.calls[0][2]['model']=='126710BLNR'
    pipeline.dry_run=True
    assert pipeline.archive_images()==[]
    pipeline.archive=None
    assert pipeline.archive_images()==[]
