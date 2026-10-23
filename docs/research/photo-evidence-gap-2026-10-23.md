# Photo evidence gap assessment and sourcing log — 2026-10-23

**Repository:** Biggregw/watch-align  
**Branch:** `experiment/archive-first-onedrive-20261023`  
**Scope:** Metadata and sourcing research only. No source photo binaries, private archive content, selection manifests, calibration data, model geometry, production code, thresholds or tolerances were changed.

## Executive result

The collection is highly unbalanced by photograph count and authenticity status. The clearest archive gaps are (1) independent GMT-Master II watches and bezel/dial variants, (2) steel Submariner Date 126610LV (green bezel), and (3) the complete absence of Yacht-Master examples. The existing 124060 photo count is large but mixes dealer-listed examples with listings explicitly identified as replicas; it is not a count of verified genuine watches.

I visually inspected four public Rolex Certified Pre-Owned (CPO) listing images as source candidates. Three are clear, near face-on candidate images (GMT-Master II 126710BLNR, Submariner Date 126610LV, Yacht-Master 126622). The fourth is a GMT-Master II 126710BLRO; the hero view is also clear, near face-on. The associated product pages report CPO status and identify individual inventory codes. However, the source site's terms prohibit reproducing or retaining site content without written permission. No image binary was downloaded or retained, no original SHA-256 could be computed, and none is counted as a verified new photograph in the Suitable Photos archive. They are documented below as leads awaiting rights approval.

## Current archive and independent-watch counts

Counts below describe the project's Suitable Photos collection unless stated otherwise. A physical-watch ID is a catalogue identity, not proof of authenticity. Dealer/CPO provenance is kept distinct from independent authentication.

| Family / reference grouping | Suitable photos now | Distinct physical watch IDs represented | Provenance reading | Main gap |
|---|---:|---:|---|---|
| Submariner 124060 | 86 | 41 | 35 dealer-listed IDs plus 6 replica-labelled IDs. Fifteen photos are from curated replica listings. Dealer listing is not equivalent to independent authentication. | The raw count overstates independent genuine coverage. Recheck the 26 uncertain originals and distinguish candidate genuine from replica-labelled material. |
| Submariner Date 126610 family | 10 | 10 | All ten catalogue entries are dealer-listed. This is listing provenance, not proof that every object was independently authenticated. | Only two are explicitly 126610LN; no selected 126610LV (green bezel). 126613LB is represented by four images, so photos are not evenly spread across variants. |
| GMT-Master II 126710 | 1 | 1 | One Bob's Watches listing, 126710BLNR. Dealer listing; not an independent authentication record. | Near-total lack of archive coverage and only one bezel colorway/watch. |
| Yacht-Master | 0 | 0 | None in the Suitable Photos archive. | No collection and no corresponding current selection-schema model enum. Keep separate from calibration data. |
| Unknown model | 11 | Not determinable | Existing batch has no source URL, catalogue row or physical-watch ID for these photos. | The image's original EXIF/source records cannot be inspected from the public review share. |

Across the 108 selected photos, 52 catalogue watch IDs are represented (41 124060 + 10 Submariner Date + 1 GMT). Six of the 41 124060 IDs are explicitly replica-labelled. The other 46 IDs have dealer-listing provenance; that alone should not be restated as independently verified genuineness. The eleven unknown-model photos could represent fewer or more watches; independence is unknown. The existing broader 124060 research catalogue and Submariner Date research dataset are not the same thing as the curated OneDrive Suitable Photos collection and should continue to be counted separately.

### Variant and marker evidence

- **124060:** The Suitable collection includes many repeat views, but 41 catalogue IDs (including six replica-labelled IDs) is the more useful count than 86 files. The repository's separate 124060 research note reports 42 dial photos across 25 physical watches in its verified catalogue subset; it is a different inclusion set and must not be added to the archive counts.
- **126610 family:** The separate research dataset `tools/research/sub126610/catalogue_126610.csv` reports 358 photos from 307 Bob's listing IDs and broad 41 mm date-model research. That does not resolve the smaller Suitable Photos collection's 126610LV gap or prove independent authenticity. Within the selected collection, the LN/LV steel variants are specifically underrepresented versus two-tone and gold variants.
- **GMT 126710:** The broader `tools/research/alpha96_calibration/catalogue_provenance_strong.csv` contains 213 listing rows / 123 listing IDs across BLNR, BLRO and GRNR and other color/material groupings; it records established dealers, auction houses and Rolex CPO separately. The reference CSV contains 45 physical-watch IDs, but only a subset joins cleanly to the strong-source catalogue. This research coverage is useful context, but only one GMT photo is in the user-facing Suitable Photos collection. Independent physical watches and provenance in the research files must not be inferred from the one archive photo.
- **Yacht-Master:** Not currently supported by a Suitable Photos schema enum. Rolex's current 126622 official model page describes the steel/platinum Rolesium 40 mm reference; official catalogue imagery is useful for model identification, not an independent physical-watch sample. Keep any future Yacht-Master examples in a separate research staging manifest until the project owner approves a schema/model-support change.

## Existing uncertain and unknown material

- There are **26 uncertain photos**, all associated with Submariner 124060: 22 marked for a full-resolution recheck and four described as lume-only or dark-lighting photos. The 22 could add repeatability views, but current catalogue associations indicate they are unlikely to add many independent physical watches. They remain uncertain pending inspection of the private originals. Hands obscuring some markers is not itself a rejection reason.
- There are **11 unknown-model photos** with no source URLs, catalogue rows or source metadata in the current batch. The public review share did not expose original EXIF/source records. Batch dimensions are not enough to identify the model or authenticity. Keep all 11 explicitly unknown; do not infer provenance or physical-watch independence.

## Public source candidates visually examined

All four images below were opened as direct public image URLs and inspected in the cloud browser. The pages identify the item as Rolex CPO and state that the CPO guarantee card confirms genuineness at sale. This is stronger provenance than a seller assertion, but the project's own records would still need to retain the exact item identity and an image hash before the image could enter a verified collection.

| Family / reference | Dealer inventory code and listing | Visual review | Collection status |
|---|---|---|---|
| GMT-Master II 126710BLNR | Watches of Switzerland, code 40618807 — https://www.watches-of-switzerland.co.uk/Rolex-Certified-Pre%2BOwned-GMT%2BMaster-II-126710BLNR/p/40618807 ; image https://content.thewosgroup.com/productimage/40618807/40618807_1.png?impolicy=hero-rolex | 1080×1080; sharp, near face-on, full dial. The CPO seal is in the image margin, not over the dial. Candidate suitable. CPO page identifies a 2021 Oystersteel 126710BLNR. | Not retained. No local SHA-256. |
| GMT-Master II 126710BLRO | Watches of Switzerland, code 406107257490 — product page https://www.watches-of-switzerland.co.uk/Rolex-Certified-Pre%2BOwned-GMT%2BMaster-II-126710BLRO/p/406107257490 ; hero image https://content.thewosgroup.com/productimage/406107257490/406107257490_1.png?impolicy=hero-rolex | 1080×1080; sharp and near face-on, full dial. CPO seal sits in image margin. Candidate suitable. The alternate `_3.png` gallery image was also inspected and rejected for strong oblique perspective. | Not retained. No local SHA-256. |
| Submariner Date 126610LV | Watches of Switzerland, code 40619401 — https://www.watches-of-switzerland.co.uk/Rolex-Certified-Pre%2BOwned-Submariner-Date-126610LV/p/40619401 ; image https://content.thewosgroup.com/productimage/40619401/40619401_1.png?impolicy=hero-rolex | 1080×1080; sharp, near face-on, dial and indices visible. CPO seal is outside the dial. Candidate suitable. | Not retained. No local SHA-256. |
| Yacht-Master 126622 | Watches of Switzerland, code 408102102490 — https://www.watches-of-switzerland.co.uk/Rolex-Certified-Pre%2BOwned-Yacht-Master-40-126622/p/408102102490 ; image https://content.thewosgroup.com/productimage/408102102490/408102102490_1.png?impolicy=hero-rolex | 1080×1080; sharp, near face-on. CPO seal is outside the dial. Candidate suitable for a separate Yacht-Master research staging set only. | Not retained. No local SHA-256. |

The product pages identify distinct inventory codes, so these are separate offered CPO stock items rather than duplicate photographs of one listed watch. This does not establish that a dealer never relisted the same physical watch under a new code; retain inventory-code identity and avoid making stronger claims without serial or service-document linkage.

### Rights and access record

- Goldsmiths' website terms state site material cannot be reproduced, published, altered or distributed without prior written permission (except permitted press releases). The inspected CPO pages are in the Watches of Switzerland Group's retail web estate. On that basis, no CPO image was downloaded for internal OneDrive retention or uploaded anywhere. A public URL is not a reuse license.
- Bob's Watches terms grant a limited personal-use license and do not grant a general reproduction license. Existing catalog links remain source references only; do not copy new listing images without permission.
- Rolex product imagery is copyrighted and was used only for reference/model identification, not collected as physical-watch evidence.
- No source image was modified. No new image files were written to OneDrive or GitHub. Since no source binary was retained, no source SHA-256 or local duplicate-hash comparison exists for these candidates.
- Rights approval or an appropriately licensed source is required before any of the four CPO candidates can become archive photographs. The rights holder would need to authorize internal research retention and copying to the private OneDrive archive.

## Validation and next actions

- The existing collection totals remain unchanged: **108 suitable (86 / 10 / 1 / 11), 26 uncertain, 283 unsuitable**, accounting for all 417 originals.
- No existing photo or manifest was modified; the 86 previously selected photos are preserved.
- No new candidate has been added to a selection manifest, run through protected dry-run verification, copied to OneDrive, or counted as verified.
- The selection schema currently supports only 124060, 126610, 126710 and unknown. Do not label Yacht-Master as unknown. Any future Yacht-Master collection requires a separately approved research staging format before calibration use.
- To finish the archive recheck, access to the private OneDrive originals is needed to inspect the 26 uncertain images and original metadata for the 11 unknowns. The public review share does not grant access to those files.
- To finish CPO sourcing, obtain written image-use permission from the applicable rights holder or identify an equivalent source with a clear reusable license, then retrieve the highest-resolution image, compute SHA-256, visually inspect, deduplicate and stage a metadata-only source record alongside the private image in OneDrive.
- Do not feed any new photograph into production calibration automatically. Keep all future external-photo research separate from calibration until independently reviewed and explicitly approved.
