# Pinned public-source Android regression photographs

These four unchanged JPEGs are the exact SHA-256-verified public dealer images
listed in `../android_fixtures.json`, with source URLs and catalogue identities.
They are research/test fixtures, not application assets, and are not packaged in
the production APK. The photographs originate from Bob's Watches; they are not
owner-supplied private photographs and no ownership of the photographs is claimed.

The two Date photographs reproduce the investigated seconds-hand failure. GMT
and No-Date photographs check the same Android/native OpenCV path in the other
supported families. Together the originals occupy about 1.7 MB.

CI runs 39305849352 and 39306132742 failed because the dealer CDN did not return
the pinned Date bytes on GitHub's runner, although all four verified originals
were already available locally. Keeping only these four originals here removes
that network dependency without changing any expected hash or substituting a
different photograph. `fetch_android.py` fails on corrupt pinned files; the
instrumentation test independently checks SHA-256 before decoding each image.
