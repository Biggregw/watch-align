from __future__ import annotations

import io
import json
import math
import shutil
import time
import uuid
from pathlib import Path
from typing import Literal

import cv2
import imageio.v2 as imageio
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from PIL import Image, ImageDraw, ImageFont

import sys

BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"

# When frozen by PyInstaller (--onefile), __file__ resolves inside a temp
# extraction folder that's wiped after every run. Static assets are
# read-only bundled resources so that's fine, but session uploads/exports
# need to live somewhere persistent next to the actual .exe instead.
if getattr(sys, "frozen", False):
    PERSIST_DIR = Path(sys.executable).resolve().parent
else:
    PERSIST_DIR = BASE_DIR

# The web interface is embedded so the Hugging Face Space only needs four root files.
_EMBEDDED_STATIC = {'index.html': '<!doctype html>\n<html lang="en">\n<head>\n  <meta charset="utf-8" />\n  <meta name="viewport" content="width=device-width, initial-scale=1" />\n  <title>Watch Align</title>\n  <link rel="stylesheet" href="/static/styles.css" />\n</head>\n<body>\n  <header class="topbar">\n    <div>\n      <div class="eyebrow">IMAGE COMPARISON</div>\n      <h1>Watch Align</h1>\n      <p>Automatically align two watch photos, match colour and lighting, inspect differences, and export a blink comparison.</p>\n    </div>\n  </header>\n\n  <main class="layout">\n    <section class="panel upload-panel">\n      <h2>1. Choose images</h2>\n      <div class="upload-grid">\n        <label class="dropzone">\n          <span class="drop-title">Reference image</span>\n          <span class="drop-subtitle">For example, a genuine watch photo</span>\n          <input id="referenceInput" type="file" accept="image/*" />\n          <span id="referenceName" class="file-name">No file selected</span>\n        </label>\n        <label class="dropzone">\n          <span class="drop-title">Candidate image</span>\n          <span class="drop-subtitle">For example, your QC photo</span>\n          <input id="candidateInput" type="file" accept="image/*" />\n          <span id="candidateName" class="file-name">No file selected</span>\n        </label>\n      </div>\n      <button id="alignButton" class="primary" disabled>Auto-align images</button>\n      <p id="status" class="status">Choose both images to begin.</p>\n    </section>\n\n    <section id="workspace" class="panel workspace hidden">\n      <div class="workspace-header">\n        <div>\n          <h2>2. Inspect the result</h2>\n          <p id="confidenceText" class="muted"></p>\n        </div>\n        <div class="tabs" role="tablist">\n          <button data-mode="blink" class="tab active">Blink</button>\n          <button data-mode="overlay" class="tab">Overlay</button>\n          <button data-mode="slider" class="tab">Slider</button>\n          <button data-mode="edges" class="tab">Edges</button>\n          <button data-mode="heatmap" class="tab">Heatmap</button>\n          <button data-mode="matchcheck" class="tab">Raw vs matched</button>\n        </div>\n      </div>\n\n      <div id="viewer" class="viewer">\n        <img id="referenceImage" alt="Reference" />\n        <img id="alignedImage" alt="Aligned candidate" class="layer" />\n        <img id="singleImage" alt="Comparison view" class="single hidden" />\n        <div id="sliderHandle" class="slider-handle hidden"></div>\n      </div>\n\n      <div class="legend">\n        <span id="legendText">Blinking between reference and aligned candidate</span>\n      </div>\n    </section>\n\n    <aside id="controls" class="panel controls hidden">\n      <h2>3. Fine-tune</h2>\n      <div class="control-row">\n        <label for="rotation">Rotation <output id="rotationOut">0.00°</output></label>\n        <input id="rotation" type="range" min="-5" max="5" step="0.05" value="0" />\n      </div>\n      <div class="control-row">\n        <label for="scale">Scale <output id="scaleOut">1.000×</output></label>\n        <input id="scale" type="range" min="0.70" max="1.50" step="0.001" value="1" />\n      </div>\n      <div class="control-row">\n        <label for="xOffset">Horizontal <output id="xOut">0 px</output></label>\n        <input id="xOffset" type="range" min="-120" max="120" step="1" value="0" />\n      </div>\n      <div class="control-row">\n        <label for="yOffset">Vertical <output id="yOut">0 px</output></label>\n        <input id="yOffset" type="range" min="-120" max="120" step="1" value="0" />\n      </div>\n      <div class="control-row">\n        <label for="opacity">Overlay opacity <output id="opacityOut">50%</output></label>\n        <input id="opacity" type="range" min="0" max="1" step="0.01" value="0.5" />\n      </div>\n\n      <div class="appearance-card">\n        <label class="toggle-row" for="logoLock">\n          <span>\n            <strong>Lock dial size, logo and text</strong>\n            <small>Matches the hour-marker ring first, then locks the crown and ROLEX printing</small>\n          </span>\n          <input id="logoLock" type="checkbox" checked />\n        </label>\n        <p class="appearance-note">Leave this on for automatic size and text alignment. Turn it off before deliberately changing scale or position with the manual controls.</p>\n      </div>\n\n      <div class="appearance-card">\n        <label class="toggle-row" for="appearanceMatch">\n          <span>\n            <strong>Match appearance</strong>\n            <small>Normalise candidate colour, exposure and contrast to the reference</small>\n          </span>\n          <input id="appearanceMatch" type="checkbox" checked />\n        </label>\n        <div id="matchStrengthRow" class="control-row compact">\n          <label for="matchStrength">Match strength <output id="matchStrengthOut">85%</output></label>\n          <input id="matchStrength" type="range" min="0" max="1" step="0.01" value="0.85" />\n        </div>\n        <p class="appearance-note">The originals remain available. Matching changes presentation only and does not alter geometry.</p>\n      </div>\n\n      <div class="button-row">\n        <button id="resetButton" class="secondary">Reset</button>\n        <button id="applyButton" class="primary">Apply</button>\n      </div>\n\n      <div class="metrics" id="metrics"></div>\n\n      <h2 class="export-title">4. Export</h2>\n      <div class="export-grid">\n        <button data-export="gif" class="secondary">Download GIF</button>\n        <button data-export="mp4" class="secondary">Download MP4</button>\n        <button data-export="png" class="secondary">Download overlay PNG</button>\n      </div>\n    </aside>\n  </main>\n\n  <footer>\n    <p>Automatic alignment is an aid, not proof of authenticity. Perspective, lighting, crystal distortion, and different hand positions can create false differences.</p>\n    <div class="support-block">\n      <span>Enjoying Watch Align? Help support its hosting and development.</span>\n      <a\n        class="coffee-button"\n        href="https://buymeacoffee.com/biggregw"\n        target="_blank"\n        rel="noopener noreferrer"\n        aria-label="Support Watch Align on Buy Me a Coffee"\n      >☕ Buy me a coffee</a>\n    </div>\n  </footer>\n\n  <script src="/static/app.js" defer></script>\n</body>\n</html>\n', 'styles.css': ':root {\n  color-scheme: dark;\n  --bg: #08111f;\n  --panel: #101b2d;\n  --panel-2: #14233a;\n  --border: #253857;\n  --text: #f4f7fb;\n  --muted: #9eb0c9;\n  --cyan: #32d5f2;\n  --cyan-2: #19b7d3;\n  --danger: #ff7979;\n  --shadow: 0 22px 70px rgba(0, 0, 0, .28);\n}\n\n* { box-sizing: border-box; }\nbody {\n  margin: 0;\n  min-height: 100vh;\n  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;\n  background:\n    radial-gradient(circle at 18% 0%, rgba(50, 213, 242, .10), transparent 28rem),\n    linear-gradient(180deg, #08111f 0%, #0b1524 100%);\n  color: var(--text);\n}\n\nbutton, input { font: inherit; }\nbutton { cursor: pointer; }\nbutton:disabled { opacity: .45; cursor: not-allowed; }\n\n.topbar {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: 2rem;\n  max-width: 1500px;\n  margin: 0 auto;\n  padding: 2.5rem 2rem 1.5rem;\n}\n.topbar h1 { margin: .2rem 0 .35rem; font-size: clamp(2rem, 4vw, 3.5rem); letter-spacing: -.045em; }\n.topbar p { margin: 0; color: var(--muted); max-width: 720px; }\n.eyebrow { color: var(--cyan); font-size: .78rem; font-weight: 800; letter-spacing: .17em; }\n.privacy { margin-top: .5rem; padding: .55rem .8rem; border: 1px solid var(--border); border-radius: 999px; color: var(--muted); white-space: nowrap; }\n\n.layout {\n  max-width: 1500px;\n  margin: 0 auto;\n  padding: 0 2rem 2rem;\n  display: grid;\n  grid-template-columns: minmax(0, 1fr) 330px;\n  gap: 1.2rem;\n  align-items: start;\n}\n.panel {\n  background: linear-gradient(180deg, rgba(20, 35, 58, .96), rgba(16, 27, 45, .96));\n  border: 1px solid var(--border);\n  border-radius: 20px;\n  box-shadow: var(--shadow);\n}\n.upload-panel { grid-column: 1 / -1; padding: 1.25rem; }\n.upload-panel h2, .workspace h2, .controls h2 { margin: 0 0 1rem; font-size: 1.05rem; }\n.upload-grid { display: grid; grid-template-columns: 1fr 1fr; gap: .9rem; margin-bottom: 1rem; }\n.dropzone {\n  display: grid;\n  gap: .25rem;\n  padding: 1.15rem;\n  border: 1px dashed #3a5a82;\n  border-radius: 15px;\n  background: rgba(7, 17, 31, .4);\n  transition: border-color .2s, transform .2s, background .2s;\n}\n.dropzone:hover { border-color: var(--cyan); background: rgba(50, 213, 242, .05); transform: translateY(-1px); }\n.dropzone input { margin-top: .6rem; width: 100%; }\n.drop-title { font-weight: 800; }\n.drop-subtitle, .file-name, .muted, .status { color: var(--muted); }\n.file-name { font-size: .85rem; overflow-wrap: anywhere; }\n\n.primary, .secondary, .tab {\n  border: 0;\n  border-radius: 11px;\n  padding: .76rem 1rem;\n  font-weight: 800;\n}\n.primary { background: linear-gradient(135deg, var(--cyan), var(--cyan-2)); color: #04202a; }\n.secondary { background: #192b45; color: var(--text); border: 1px solid #2b4569; }\n.status { margin: .8rem 0 0; font-size: .92rem; }\n.status.error { color: var(--danger); }\n\n.workspace { padding: 1rem; min-width: 0; }\n.workspace-header { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; margin-bottom: .9rem; }\n.tabs { display: flex; flex-wrap: wrap; gap: .4rem; justify-content: flex-end; }\n.tab { padding: .55rem .72rem; background: #13243b; color: var(--muted); border: 1px solid transparent; }\n.tab.active { color: #051820; background: var(--cyan); }\n\n.viewer {\n  position: relative;\n  display: grid;\n  place-items: center;\n  min-height: 560px;\n  background:\n    linear-gradient(45deg, #0d1727 25%, transparent 25%),\n    linear-gradient(-45deg, #0d1727 25%, transparent 25%),\n    linear-gradient(45deg, transparent 75%, #0d1727 75%),\n    linear-gradient(-45deg, transparent 75%, #0d1727 75%), #101c2e;\n  background-size: 26px 26px;\n  background-position: 0 0, 0 13px, 13px -13px, -13px 0px;\n  border-radius: 15px;\n  overflow: hidden;\n  border: 1px solid #203551;\n}\n.viewer img { max-width: 100%; max-height: 74vh; object-fit: contain; display: block; }\n.viewer .layer, .viewer .single { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }\n.viewer.blinking .layer { animation: blink 1.8s steps(1, end) infinite; }\n@keyframes blink { 0%, 49.9% { opacity: 0; } 50%, 100% { opacity: 1; } }\n.slider-handle { position: absolute; top: 0; bottom: 0; left: 50%; width: 3px; background: white; box-shadow: 0 0 0 1px rgba(0,0,0,.35); pointer-events: none; }\n.slider-handle::after { content: "↔"; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); background: white; color: #07111f; border-radius: 999px; padding: .45rem; font-weight: 900; }\n.legend { padding: .7rem .2rem .1rem; color: var(--muted); font-size: .88rem; }\n\n.controls { padding: 1rem; position: sticky; top: 1rem; }\n.control-row { margin-bottom: 1rem; }\n.control-row label { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: .45rem; font-size: .9rem; color: var(--muted); }\n.control-row output { color: var(--text); font-variant-numeric: tabular-nums; }\n.control-row input { width: 100%; accent-color: var(--cyan); }\n.button-row { display: grid; grid-template-columns: 1fr 1fr; gap: .6rem; }\n.metrics { margin-top: 1rem; padding: .8rem; border-radius: 13px; background: rgba(7, 17, 31, .6); border: 1px solid #223957; font-size: .84rem; color: var(--muted); line-height: 1.55; }\n.metrics strong { color: var(--text); }\n.export-title { margin-top: 1.4rem !important; }\n.export-grid { display: grid; gap: .55rem; }\n\nfooter { max-width: 1500px; margin: 0 auto; padding: 0 2rem 2rem; color: var(--muted); font-size: .83rem; }\nfooter > p { margin: 0; }\n.support-block {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 1rem;\n  margin-top: 1rem;\n  padding-top: 1rem;\n  border-top: 1px solid var(--border);\n}\n.coffee-button {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  flex: 0 0 auto;\n  padding: .68rem .95rem;\n  border: 1px solid #2b4569;\n  border-radius: 11px;\n  background: #192b45;\n  color: var(--text);\n  font-size: .88rem;\n  font-weight: 800;\n  text-decoration: none;\n  transition: transform .15s ease, border-color .15s ease, background .15s ease;\n}\n.coffee-button:hover {\n  transform: translateY(-1px);\n  border-color: var(--cyan);\n  background: #1d3452;\n}\n.coffee-button:focus-visible {\n  outline: 3px solid rgba(50, 213, 242, .35);\n  outline-offset: 3px;\n}\n@media (max-width: 700px) {\n  .support-block { align-items: flex-start; flex-direction: column; }\n}\n.hidden { display: none !important; }\n\n@media (max-width: 980px) {\n  .layout { grid-template-columns: 1fr; }\n  .controls { position: static; }\n  .workspace-header { display: block; }\n  .tabs { justify-content: flex-start; margin-top: .7rem; }\n}\n@media (max-width: 680px) {\n  .topbar { display: block; padding: 1.5rem 1rem 1rem; }\n  .privacy { display: inline-block; margin-top: 1rem; }\n  .layout { padding: 0 1rem 1rem; }\n  .upload-grid { grid-template-columns: 1fr; }\n  .viewer { min-height: 420px; }\n}\n\n.appearance-card {\n  margin: 1.1rem 0;\n  padding: .9rem;\n  border: 1px solid #2b4569;\n  border-radius: 14px;\n  background: rgba(7, 17, 31, .55);\n}\n.toggle-row {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 1rem;\n  cursor: pointer;\n}\n.toggle-row span { display: grid; gap: .2rem; }\n.toggle-row strong { color: var(--text); }\n.toggle-row small { color: var(--muted); line-height: 1.35; }\n.toggle-row input {\n  width: 1.2rem;\n  height: 1.2rem;\n  accent-color: var(--cyan);\n  flex: 0 0 auto;\n}\n.control-row.compact { margin-top: .9rem; margin-bottom: .55rem; }\n.control-row.disabled { opacity: .38; pointer-events: none; }\n.appearance-note {\n  margin: .45rem 0 0;\n  color: var(--muted);\n  font-size: .78rem;\n  line-height: 1.45;\n}\n', 'app.js': 'const state = {\n  sessionId: null,\n  images: null,\n  mode: "blink",\n  sliderPercent: 50,\n  renderTimer: null,\n  renderSequence: 0,\n  renderController: null,\n};\n\nconst $ = (id) => document.getElementById(id);\nconst referenceInput = $("referenceInput");\nconst candidateInput = $("candidateInput");\nconst alignButton = $("alignButton");\nconst statusText = $("status");\nconst workspace = $("workspace");\nconst controls = $("controls");\nconst viewer = $("viewer");\nconst referenceImage = $("referenceImage");\nconst alignedImage = $("alignedImage");\nconst singleImage = $("singleImage");\nconst sliderHandle = $("sliderHandle");\n\nfunction updateFileState() {\n  $("referenceName").textContent = referenceInput.files[0]?.name || "No file selected";\n  $("candidateName").textContent = candidateInput.files[0]?.name || "No file selected";\n  alignButton.disabled = !(referenceInput.files[0] && candidateInput.files[0]);\n  if (!alignButton.disabled) statusText.textContent = "Ready to align.";\n}\nreferenceInput.addEventListener("change", updateFileState);\ncandidateInput.addEventListener("change", updateFileState);\n\nfunction setBusy(message) {\n  statusText.classList.remove("error");\n  statusText.textContent = message;\n  alignButton.disabled = true;\n}\n\nfunction showError(error) {\n  statusText.classList.add("error");\n  statusText.textContent = error?.message || String(error);\n  updateFileState();\n}\n\nasync function api(url, options = {}) {\n  const response = await fetch(url, options);\n  const contentType = response.headers.get("content-type") || "";\n  const body = contentType.includes("application/json") ? await response.json() : await response.text();\n  if (!response.ok) throw new Error(body.detail || body || `Request failed: ${response.status}`);\n  return body;\n}\n\nalignButton.addEventListener("click", async () => {\n  const data = new FormData();\n  data.append("reference", referenceInput.files[0]);\n  data.append("candidate", candidateInput.files[0]);\n  setBusy("Detecting watch features and estimating alignment…");\n  try {\n    const result = await api("/api/align", { method: "POST", body: data });\n    state.sessionId = result.session_id;\n    state.images = result.images;\n    applyImages(result.images);\n    showMetrics(result.metrics);\n    workspace.classList.remove("hidden");\n    controls.classList.remove("hidden");\n    statusText.textContent = "Alignment complete. Fine-tune if required.";\n    statusText.classList.remove("error");\n    switchMode("blink");\n  } catch (error) {\n    showError(error);\n  }\n  updateFileState();\n});\n\nfunction applyImages(images) {\n  state.images = images;\n  referenceImage.src = images.reference;\n  alignedImage.src = images.aligned;\n  if (state.mode === "overlay") singleImage.src = images.overlay;\n  if (state.mode === "edges") singleImage.src = images.edges;\n  if (state.mode === "heatmap") singleImage.src = images.heatmap;\n}\n\nfunction showMetrics(metrics) {\n  const confidence = metrics.confidence || "unknown";\n  const appearance = metrics.appearance || {};\n  const appearanceHtml = appearance.enabled\n    ? `\n      <div><strong>Appearance match:</strong> ${Math.round((appearance.strength ?? 0) * 100)}%</div>\n      <div><strong>LAB distance:</strong> ${appearance.mean_lab_distance_before ?? "n/a"} → ${appearance.mean_lab_distance_after ?? "n/a"}</div>\n      <div><strong>Appearance improvement:</strong> ${appearance.appearance_improvement != null ? Math.round(appearance.appearance_improvement * 100) + "%" : appearance.status || "n/a"}</div>\n    `\n    : `<div><strong>Appearance match:</strong> off</div>`;\n  $("confidenceText").textContent = `Automatic alignment confidence: ${confidence}`;\n  const rotationDetails = metrics.initial_marker_rotation_deg != null\n    ? `<div><strong>Marker rotation:</strong> ${metrics.initial_marker_rotation_deg}°</div>\n       <div><strong>Polar rotation:</strong> ${metrics.initial_polar_rotation_deg ?? "n/a"}°</div>`\n    : "";\n  const eccDetails = metrics.ecc_score != null\n    ? `<div><strong>Position refinement:</strong> ECC ${metrics.ecc_score} ${metrics.ecc_applied ? "applied" : "not applied"}</div>`\n    : "";\n  const dialDetails = appearance.dial_geometry_lock_reason != null\n    ? `<div><strong>Dial-size lock:</strong> ${appearance.dial_geometry_lock_applied ? "applied" : appearance.dial_geometry_lock_reason} ${appearance.dial_geometry_score ? `(score ${appearance.dial_geometry_score}, scale ${appearance.dial_geometry_scale}×)` : ""}</div>`\n    : "";\n  const logoDetails = appearance.logo_lock_reason != null\n    ? `<div><strong>Logo/text lock:</strong> ${appearance.logo_lock_applied ? "applied" : appearance.logo_lock_reason} ${appearance.logo_lock_score ? `(score ${appearance.logo_lock_score}, ${appearance.logo_lock_translation_px ?? 0}px)` : ""}</div>`\n    : "";\n  $("metrics").innerHTML = `\n    <div><strong>Method:</strong> ${metrics.alignment_method ?? "automatic"}</div>\n    <div><strong>Confidence:</strong> ${confidence} (${metrics.confidence_score ?? "n/a"})</div>\n    <div><strong>Geometry checks:</strong> ${metrics.inliers ?? "n/a"} / ${metrics.matches ?? "n/a"}</div>\n    <div><strong>Detected rotation:</strong> ${metrics.detected_rotation_deg}°</div>\n    <div><strong>Detected scale:</strong> ${metrics.detected_scale}×</div>\n    ${rotationDetails}\n    ${eccDetails}\n    ${dialDetails}\n    ${logoDetails}\n    ${appearanceHtml}\n  `;\n}\n\nfunction switchMode(mode) {\n  state.mode = mode;\n  document.querySelectorAll(".tab").forEach((button) => button.classList.toggle("active", button.dataset.mode === mode));\n  viewer.classList.remove("blinking");\n  singleImage.classList.add("hidden");\n  sliderHandle.classList.add("hidden");\n  referenceImage.classList.remove("hidden");\n  alignedImage.classList.remove("hidden");\n  alignedImage.style.clipPath = "none";\n  alignedImage.style.opacity = "1";\n  referenceImage.src = state.images.reference;\n  alignedImage.src = state.images.aligned;\n\n  const matched = $("appearanceMatch").checked;\n  const legends = {\n    blink: `Blinking between reference and aligned candidate${matched ? " with appearance matching" : ""}`,\n    overlay: `Opacity blend of both images${matched ? " after appearance matching" : ""}`,\n    slider: `Drag across the image to reveal the aligned candidate${matched ? " after appearance matching" : ""}`,\n    edges: "Cyan = reference edges, magenta = candidate edges, white = overlap",\n    heatmap: `Brighter areas indicate larger pixel differences${matched ? " after appearance matching" : ""}`,\n    matchcheck: "Blinking between the raw aligned candidate and its appearance-matched version",\n  };\n  $("legendText").textContent = legends[mode];\n\n  if (mode === "blink") {\n    viewer.classList.add("blinking");\n  } else if (mode === "matchcheck") {\n    referenceImage.src = state.images.aligned_raw;\n    alignedImage.src = state.images.aligned_matched;\n    viewer.classList.add("blinking");\n  } else if (mode === "slider") {\n    sliderHandle.classList.remove("hidden");\n    alignedImage.style.clipPath = `inset(0 ${100 - state.sliderPercent}% 0 0)`;\n    sliderHandle.style.left = `${state.sliderPercent}%`;\n  } else {\n    referenceImage.classList.add("hidden");\n    alignedImage.classList.add("hidden");\n    singleImage.classList.remove("hidden");\n    singleImage.src = state.images[mode];\n  }\n}\n\ndocument.querySelectorAll(".tab").forEach((button) => button.addEventListener("click", () => switchMode(button.dataset.mode)));\n\nviewer.addEventListener("pointerdown", (event) => {\n  if (state.mode !== "slider") return;\n  viewer.setPointerCapture(event.pointerId);\n  updateSlider(event);\n});\nviewer.addEventListener("pointermove", (event) => {\n  if (state.mode !== "slider" || !viewer.hasPointerCapture(event.pointerId)) return;\n  updateSlider(event);\n});\nfunction updateSlider(event) {\n  const rect = viewer.getBoundingClientRect();\n  state.sliderPercent = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100));\n  alignedImage.style.clipPath = `inset(0 ${100 - state.sliderPercent}% 0 0)`;\n  sliderHandle.style.left = `${state.sliderPercent}%`;\n}\n\nconst controlsMap = {\n  rotation: ["rotationOut", (v) => `${Number(v).toFixed(2)}°`],\n  scale: ["scaleOut", (v) => `${Number(v).toFixed(3)}×`],\n  xOffset: ["xOut", (v) => `${Math.round(v)} px`],\n  yOffset: ["yOut", (v) => `${Math.round(v)} px`],\n  opacity: ["opacityOut", (v) => `${Math.round(v * 100)}%`],\n  matchStrength: ["matchStrengthOut", (v) => `${Math.round(v * 100)}%`],\n};\n\nObject.entries(controlsMap).forEach(([id, [outputId, formatter]]) => {\n  $(id).addEventListener("input", () => {\n    $(outputId).textContent = formatter($(id).value);\n    clearTimeout(state.renderTimer);\n    state.renderTimer = setTimeout(renderAdjustments, 220);\n  });\n});\n\nfunction renderPayload() {\n  return {\n    session_id: state.sessionId,\n    rotation: Number($("rotation").value),\n    scale: Number($("scale").value),\n    x: Number($("xOffset").value),\n    y: Number($("yOffset").value),\n    opacity: Number($("opacity").value),\n    appearance_match: $("appearanceMatch").checked,\n    match_strength: Number($("matchStrength").value),\n    logo_lock: $("logoLock").checked,\n  };\n}\n\nasync function renderAdjustments() {\n  if (!state.sessionId) return;\n\n  // A quick sequence of slider movements can leave several requests in flight.\n  // Cancel the older request and only apply the newest response so stale renders\n  // cannot make another control appear to jump backwards or change by itself.\n  const sequence = ++state.renderSequence;\n  state.renderController?.abort();\n  const controller = new AbortController();\n  state.renderController = controller;\n\n  try {\n    const result = await api("/api/render", {\n      method: "POST",\n      headers: { "Content-Type": "application/json" },\n      body: JSON.stringify(renderPayload()),\n      signal: controller.signal,\n    });\n    if (sequence !== state.renderSequence) return;\n    applyImages(result.images);\n    showMetrics(result.metrics);\n    switchMode(state.mode);\n  } catch (error) {\n    if (error?.name !== "AbortError" && sequence === state.renderSequence) {\n      showError(error);\n    }\n  } finally {\n    if (sequence === state.renderSequence) state.renderController = null;\n  }\n}\n\n$("applyButton").addEventListener("click", () => {\n  clearTimeout(state.renderTimer);\n  renderAdjustments();\n});\n$("resetButton").addEventListener("click", () => {\n  $("rotation").value = 0;\n  $("scale").value = 1;\n  $("xOffset").value = 0;\n  $("yOffset").value = 0;\n  $("opacity").value = .5;\n  $("appearanceMatch").checked = true;\n  $("logoLock").checked = true;\n  $("matchStrength").value = .85;\n  $("matchStrengthRow").classList.remove("disabled");\n  Object.entries(controlsMap).forEach(([id, [outputId, formatter]]) => $(outputId).textContent = formatter($(id).value));\n  renderAdjustments();\n});\n\n$("logoLock").addEventListener("change", () => {\n  clearTimeout(state.renderTimer);\n  state.renderTimer = setTimeout(renderAdjustments, 100);\n});\n\n$("appearanceMatch").addEventListener("change", () => {\n  $("matchStrengthRow").classList.toggle("disabled", !$("appearanceMatch").checked);\n  clearTimeout(state.renderTimer);\n  state.renderTimer = setTimeout(renderAdjustments, 100);\n});\n\ndocument.querySelectorAll("[data-export]").forEach((button) => {\n  button.addEventListener("click", async () => {\n    if (!state.sessionId) return;\n    const format = button.dataset.export;\n    const original = button.textContent;\n    button.disabled = true;\n    button.textContent = "Preparing…";\n    try {\n      const result = await api("/api/export", {\n        method: "POST",\n        headers: { "Content-Type": "application/json" },\n        body: JSON.stringify({ ...renderPayload(), format, frame_ms: 900 }),\n      });\n      const link = document.createElement("a");\n      link.href = result.download_url;\n      link.download = result.filename;\n      document.body.appendChild(link);\n      link.click();\n      link.remove();\n    } catch (error) {\n      showError(error);\n    } finally {\n      button.disabled = false;\n      button.textContent = original;\n    }\n  });\n});\n'}
STATIC_DIR.mkdir(parents=True, exist_ok=True)
for _name, _content in _EMBEDDED_STATIC.items():
    _path = STATIC_DIR / _name
    if not _path.exists() or _path.read_text(encoding="utf-8") != _content:
        _path.write_text(_content, encoding="utf-8")

SESSIONS_DIR = PERSIST_DIR / "runtime" / "sessions"
SESSIONS_DIR.mkdir(parents=True, exist_ok=True)

MAX_UPLOAD_BYTES = 20 * 1024 * 1024
MAX_PIXELS = 30_000_000
SESSION_TTL_SECONDS = 24 * 60 * 60
MAX_WORKING_SIDE = 2200
COMPARISON_MARGIN_RATIO = 1.72
MIN_COMPARISON_SIZE = 640
MAX_COMPARISON_SIZE = 1600

app = FastAPI(title="Watch Align MVP", version="0.9.4")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
app.mount("/files", StaticFiles(directory=SESSIONS_DIR), name="files")


class RenderRequest(BaseModel):
    session_id: str
    rotation: float = Field(0.0, ge=-15.0, le=15.0)
    scale: float = Field(1.0, ge=0.6, le=1.4)
    x: float = Field(0.0, ge=-400.0, le=400.0)
    y: float = Field(0.0, ge=-400.0, le=400.0)
    opacity: float = Field(0.5, ge=0.0, le=1.0)
    appearance_match: bool = True
    match_strength: float = Field(0.85, ge=0.0, le=1.0)
    logo_lock: bool = True


class ExportRequest(RenderRequest):
    format: Literal["gif", "mp4", "png"]
    frame_ms: int = Field(900, ge=250, le=3000)


def cleanup_old_sessions() -> None:
    now = time.time()
    for path in SESSIONS_DIR.iterdir():
        if not path.is_dir():
            continue
        try:
            if now - path.stat().st_mtime > SESSION_TTL_SECONDS:
                shutil.rmtree(path, ignore_errors=True)
        except FileNotFoundError:
            pass


def read_upload(upload: UploadFile) -> np.ndarray:
    raw = upload.file.read(MAX_UPLOAD_BYTES + 1)
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Image is larger than 20 MB.")
    data = np.frombuffer(raw, dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_UNCHANGED)
    if image is None:
        raise HTTPException(status_code=400, detail=f"Could not read {upload.filename or 'image'}.")

    h, w = image.shape[:2]
    if h * w > MAX_PIXELS:
        raise HTTPException(status_code=413, detail="Image dimensions are too large.")

    if image.ndim == 2:
        image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
    elif image.shape[2] == 4:
        # Composite transparency onto neutral dark grey, which helps feature matching.
        bgr = image[:, :, :3].astype(np.float32)
        alpha = image[:, :, 3:4].astype(np.float32) / 255.0
        background = np.full_like(bgr, 24, dtype=np.float32)
        image = (bgr * alpha + background * (1.0 - alpha)).astype(np.uint8)
    return image


def resize_max(image: np.ndarray, max_side: int = MAX_WORKING_SIDE) -> np.ndarray:
    h, w = image.shape[:2]
    scale = min(1.0, max_side / max(h, w))
    if scale == 1.0:
        return image
    return cv2.resize(image, (round(w * scale), round(h * scale)), interpolation=cv2.INTER_AREA)


def center_mask(shape: tuple[int, int]) -> np.ndarray:
    h, w = shape
    mask = np.zeros((h, w), dtype=np.uint8)
    center = (w // 2, h // 2)
    axes = (max(20, int(w * 0.46)), max(20, int(h * 0.46)))
    cv2.ellipse(mask, center, axes, 0, 0, 360, 255, -1)
    return mask


def detect_watch_circle(image: np.ndarray) -> tuple[float, float, float] | None:
    """Detect the dial/bezel circle used as the stable geometric anchor.

    The detector works on a downscaled copy and prefers a strong circle near the
    centre with a radius close to 30% of the shorter image dimension. This is far
    more reliable for watch photographs than unconstrained feature matching,
    because hands, text and backgrounds can otherwise dominate the transform.
    """
    original_h, original_w = image.shape[:2]
    downscale = min(1.0, 1000.0 / max(original_h, original_w))
    if downscale < 1.0:
        working = cv2.resize(
            image,
            (round(original_w * downscale), round(original_h * downscale)),
            interpolation=cv2.INTER_LINEAR,
        )
    else:
        working = image.copy()

    gray = cv2.cvtColor(working, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (9, 9), 2.0)
    h, w = gray.shape
    shorter = min(h, w)

    circles: list[tuple[float, float, float]] = []
    for accumulator_threshold in (60, 55, 50, 45, 40, 35):
        detected = cv2.HoughCircles(
            gray,
            cv2.HOUGH_GRADIENT,
            dp=1.2,
            minDist=max(80, shorter // 3),
            param1=120,
            param2=accumulator_threshold,
            minRadius=max(30, int(shorter * 0.20)),
            maxRadius=max(40, int(shorter * 0.40)),
        )
        if detected is not None:
            circles.extend(tuple(map(float, item)) for item in detected[0])
        if len(circles) >= 3:
            break

    if not circles:
        return None

    image_center_x, image_center_y = w / 2.0, h / 2.0

    def score(circle: tuple[float, float, float]) -> float:
        x, y, radius = circle
        centre_distance = math.hypot(
            (x - image_center_x) / max(1.0, w),
            (y - image_center_y) / max(1.0, h),
        )
        radius_distance = abs(radius / max(1.0, shorter) - 0.30)
        return centre_distance * 1.4 + radius_distance

    x, y, radius = min(circles, key=score)
    return x / downscale, y / downscale, radius / downscale



def auto_crop_around_watch(
    image: np.ndarray,
    circle: tuple[float, float, float],
    margin_ratio: float = 1.65,
) -> tuple[np.ndarray, tuple[int, int]]:
    center_x, center_y, radius = circle
    h, w = image.shape[:2]
    half = int(max(radius * margin_ratio, radius * 1.45))
    x0 = max(0, int(round(center_x - half)))
    y0 = max(0, int(round(center_y - half)))
    x1 = min(w, int(round(center_x + half)))
    y1 = min(h, int(round(center_y + half)))
    cropped = image[y0:y1, x0:x1].copy()
    return cropped, (x0, y0)


def estimate_crystal_radius(
    image: np.ndarray,
    circle: tuple[float, float, float],
    search_inner: float = 0.82,
    search_outer: float = 1.38,
) -> float:
    center_x, center_y, base_radius = circle
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    yy, xx = np.indices(gray.shape, dtype=np.float32)
    dx = xx - center_x
    dy = yy - center_y
    radial_distance = np.sqrt(dx * dx + dy * dy)
    radial_distance = np.maximum(radial_distance, 1e-6)
    radial_unit_x = dx / radial_distance
    radial_unit_y = dy / radial_distance
    radial_gradient = np.abs(gx * radial_unit_x + gy * radial_unit_y)
    angular_position = np.degrees(np.arctan2(dx, -dy)) % 360.0
    suppress = (
        ((angular_position > 135) & (angular_position < 225))
        | ((angular_position > 70) & (angular_position < 110))
    )
    radial_gradient = np.where(suppress, 0.0, radial_gradient)
    r_min = max(10, int(base_radius * search_inner))
    r_max = min(int(min(gray.shape[:2]) * 0.49), int(base_radius * search_outer))
    if r_max <= r_min + 5:
        return float(base_radius)
    radii = np.arange(r_min, r_max + 1)
    profile = []
    for r in radii:
        mask = (radial_distance >= r - 1.2) & (radial_distance <= r + 1.2)
        values = radial_gradient[mask]
        profile.append(float(np.percentile(values, 85)) if values.size else 0.0)
    profile = np.array(profile, dtype=np.float32)
    if len(profile) >= 7:
        profile = cv2.GaussianBlur(profile.reshape(1, -1), (1, 7), 0).reshape(-1)
    best_idx = int(np.argmax(profile))
    return float(radii[best_idx])


def refine_crystal_circle(
    image: np.ndarray,
    circle: tuple[float, float, float],
) -> tuple[float, float, float]:
    center_x, center_y, _ = circle
    refined_radius = estimate_crystal_radius(image, circle)
    return float(center_x), float(center_y), float(refined_radius)


def translate_affine(dx: float, dy: float) -> np.ndarray:
    return np.array([[1.0, 0.0, dx], [0.0, 1.0, dy]], dtype=np.float64)

def detect_refined_circle_full(image: np.ndarray) -> tuple[float, float, float] | None:
    initial = detect_watch_circle(image)
    if initial is None:
        return None
    crop, offset = auto_crop_around_watch(image, initial)
    refined = refine_crystal_circle(
        crop,
        (
            initial[0] - offset[0],
            initial[1] - offset[1],
            initial[2],
        ),
    )
    return float(refined[0] + offset[0]), float(refined[1] + offset[1]), float(refined[2])


def recenter_and_scale_aligned(
    reference_circle: tuple[float, float, float],
    aligned: np.ndarray,
    valid_mask: np.ndarray,
) -> tuple[np.ndarray, np.ndarray, dict]:
    aligned_circle = detect_refined_circle_full(aligned)
    if aligned_circle is None:
        return aligned, valid_mask, {
            "post_circle_correction_applied": False,
            "post_circle_correction_reason": "aligned circle not found",
        }

    correction = circle_similarity_matrix(reference_circle, aligned_circle, 0.0)
    _, sx, sy, anisotropy = affine_decomposition(correction)
    average_scale = (sx + sy) / 2.0
    translation = math.hypot(float(correction[0, 2]), float(correction[1, 2]))
    h, w = aligned.shape[:2]
    plausible = (
        0.96 <= average_scale <= 1.04
        and anisotropy <= 1.02
        and translation <= 0.10 * min(h, w)
    )
    if not plausible:
        return aligned, valid_mask, {
            "post_circle_correction_applied": False,
            "post_circle_correction_reason": "correction outside safety bounds",
            "post_circle_scale": round(float(average_scale), 5),
            "post_circle_translation_px": round(float(translation), 2),
            "post_circle_anisotropy": round(float(anisotropy), 5),
        }

    corrected = cv2.warpAffine(
        aligned,
        correction,
        (w, h),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(18, 18, 18),
    )
    corrected_mask = cv2.warpAffine(
        valid_mask,
        correction,
        (w, h),
        flags=cv2.INTER_NEAREST,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=0,
    )
    metrics = {
        "post_circle_correction_applied": True,
        "post_circle_scale": round(float(average_scale), 5),
        "post_circle_translation_px": round(float(translation), 2),
        "post_circle_anisotropy": round(float(anisotropy), 5),
        "aligned_circle": [round(float(v), 2) for v in aligned_circle],
    }
    return corrected, corrected_mask, metrics


def crop_to_watch_canvas(
    image: np.ndarray,
    circle: tuple[float, float, float],
    margin_ratio: float = 1.72,
    output_size: int | None = None,
    border_value: tuple[int, int, int] = (18, 18, 18),
    interpolation: int = cv2.INTER_LINEAR,
) -> tuple[np.ndarray, int]:
    center_x, center_y, radius = circle
    if output_size is None:
        output_size = int(round(radius * margin_ratio * 2.0))
    output_size = max(256, int(output_size))
    if output_size % 2 == 1:
        output_size += 1
    matrix = translate_affine(output_size / 2.0 - center_x, output_size / 2.0 - center_y)
    cropped = cv2.warpAffine(
        image,
        matrix,
        (output_size, output_size),
        flags=interpolation,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=border_value,
    )
    return cropped, output_size


def normalise_display_views(
    reference: np.ndarray,
    aligned_raw: np.ndarray,
    aligned_matched: np.ndarray,
    valid_mask: np.ndarray,
    logo_lock: bool = True,
) -> tuple[dict, dict]:
    reference_circle = detect_refined_circle_full(reference)
    if reference_circle is None:
        h, w = reference.shape[:2]
        reference_circle = (w / 2.0, h / 2.0, min(h, w) * 0.30)

    aligned_raw_corrected, valid_mask_corrected, correction_metrics = recenter_and_scale_aligned(
        reference_circle, aligned_raw, valid_mask
    )
    aligned_matched_corrected = aligned_matched
    if correction_metrics.get("post_circle_correction_applied"):
        h, w = aligned_matched.shape[:2]
        aligned_circle = correction_metrics.get("aligned_circle")
        if aligned_circle is not None:
            correction = circle_similarity_matrix(reference_circle, tuple(float(v) for v in aligned_circle), 0.0)
            aligned_matched_corrected = cv2.warpAffine(
                aligned_matched,
                correction,
                (w, h),
                flags=cv2.INTER_LINEAR,
                borderMode=cv2.BORDER_CONSTANT,
                borderValue=(18, 18, 18),
            )

    output_size = int(round(reference_circle[2] * 1.72 * 2.0))
    reference_view, output_size = crop_to_watch_canvas(
        reference,
        reference_circle,
        output_size=output_size,
        border_value=(245, 245, 245),
    )
    aligned_raw_view, _ = crop_to_watch_canvas(aligned_raw_corrected, reference_circle, output_size=output_size)
    aligned_matched_view, _ = crop_to_watch_canvas(aligned_matched_corrected, reference_circle, output_size=output_size)
    valid_mask_view, _ = crop_to_watch_canvas(
        cv2.cvtColor(valid_mask_corrected, cv2.COLOR_GRAY2BGR),
        reference_circle,
        output_size=output_size,
        border_value=(0, 0, 0),
        interpolation=cv2.INTER_NEAREST,
    )
    valid_mask_view = valid_mask_view[:, :, 0]

    if logo_lock:
        display_circle = (output_size / 2.0, output_size / 2.0, float(reference_circle[2]))
        identity = np.array([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]], dtype=np.float64)
        logo_matrix, logo_metrics = refine_logo_text_alignment(
            reference_view, aligned_raw_view, identity, display_circle
        )
        if logo_metrics.get("logo_lock_applied"):
            aligned_raw_view = cv2.warpAffine(
                aligned_raw_view, logo_matrix, (output_size, output_size),
                flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(18, 18, 18),
            )
            aligned_matched_view = cv2.warpAffine(
                aligned_matched_view, logo_matrix, (output_size, output_size),
                flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(18, 18, 18),
            )
            valid_mask_view = cv2.warpAffine(
                valid_mask_view, logo_matrix, (output_size, output_size),
                flags=cv2.INTER_NEAREST, borderMode=cv2.BORDER_CONSTANT, borderValue=0,
            )
    else:
        logo_metrics = {
            "logo_lock_applied": False,
            "logo_lock_reason": "disabled",
            "logo_lock_score": 0.0,
            "logo_lock_rotation_deg": 0.0,
            "logo_lock_translation_px": 0.0,
        }

    correction_metrics = {**correction_metrics, **logo_metrics}
    overlay_raw = cv2.addWeighted(reference_view, 0.5, aligned_matched_view, 0.5, 0.0)
    return {
        "reference": reference_view,
        "aligned_raw": aligned_raw_view,
        "aligned_matched": aligned_matched_view,
        "valid_mask": valid_mask_view,
        "overlay": overlay_raw,
        "reference_circle": [round(float(v), 2) for v in reference_circle],
        "normalized_canvas_size": output_size,
    }, correction_metrics


def geometry_highpass(image: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray).astype(np.float32)
    blurred = cv2.GaussianBlur(gray, (0, 0), 3.0)
    highpass = np.clip(gray - blurred, 0.0, None)
    return gray, highpass


def estimate_hour_marker_orientation(
    image: np.ndarray,
    circle: tuple[float, float, float],
) -> tuple[float | None, int]:
    """Estimate watch rotation from the twelve hour-marker sectors.

    Each sector is measured independently in the dial annulus. Thin hands and
    central text are mostly excluded, and a robust median rejects individual
    sectors contaminated by reflections or hands.
    """
    center_x, center_y, radius = circle
    gray, highpass = geometry_highpass(image)
    yy, xx = np.indices(gray.shape, dtype=np.float32)
    dx = xx - center_x
    dy = yy - center_y
    radial_distance = np.sqrt(dx * dx + dy * dy)
    # Clockwise angle, with 0 degrees at 12 o'clock.
    angular_position = np.degrees(np.arctan2(dx, -dy)) % 360.0
    angular_radians = np.radians(angular_position)

    sector_offsets: list[float] = []
    for target in range(0, 360, 30):
        angular_delta = ((angular_position - target + 180.0) % 360.0) - 180.0
        sector_mask = (
            (np.abs(angular_delta) < 11.0)
            & (radial_distance > radius * 0.55)
            & (radial_distance < radius * 0.86)
        )
        values = highpass[sector_mask]
        if values.size < 50:
            continue

        threshold = float(np.percentile(values, 89))
        weights = np.where(sector_mask & (highpass >= threshold), highpass, 0.0)
        weight_sum = float(weights.sum())
        if weight_sum <= 1.0:
            continue

        sine_sum = float((weights * np.sin(angular_radians)).sum())
        cosine_sum = float((weights * np.cos(angular_radians)).sum())
        measured = math.degrees(math.atan2(sine_sum, cosine_sum)) % 360.0
        offset = ((measured - target + 180.0) % 360.0) - 180.0
        sector_offsets.append(float(offset))

    if len(sector_offsets) < 6:
        return None, 0

    median_offset = float(np.median(sector_offsets))
    residuals = np.abs(
        np.array([((value - median_offset + 180.0) % 360.0) - 180.0 for value in sector_offsets])
    )
    keep = residuals < 2.5
    if int(keep.sum()) >= 5:
        median_offset = float(np.median(np.array(sector_offsets)[keep]))
    return median_offset, int(keep.sum())


def annular_signature(
    image: np.ndarray,
    circle: tuple[float, float, float],
    angle_samples: int = 1440,
    inner_ratio: float = 0.68,
    outer_ratio: float = 1.05,
) -> np.ndarray:
    center_x, center_y, radius = circle
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    gradient_x = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gradient_y = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    magnitude = np.log1p(cv2.magnitude(gradient_x, gradient_y))

    maximum_radius = max(12, int(radius * outer_ratio))
    polar = cv2.warpPolar(
        magnitude,
        (maximum_radius, angle_samples),
        (float(center_x), float(center_y)),
        float(maximum_radius),
        cv2.WARP_POLAR_LINEAR,
    )
    start = max(0, int(radius * inner_ratio))
    stop = min(maximum_radius, int(radius * outer_ratio))
    region = polar[:, start:stop]
    if region.size == 0:
        return np.zeros(angle_samples, dtype=np.float32)

    region = (region - region.mean(axis=0, keepdims=True)) / (region.std(axis=0, keepdims=True) + 1e-6)
    signature = region.mean(axis=1)
    signature = cv2.GaussianBlur(signature.reshape(-1, 1), (1, 0), sigmaX=0, sigmaY=2.0).ravel()
    return ((signature - signature.mean()) / (signature.std() + 1e-6)).astype(np.float32)


def estimate_polar_rotation(
    reference: np.ndarray,
    candidate: np.ndarray,
    reference_circle: tuple[float, float, float],
    candidate_circle: tuple[float, float, float],
    search_limit_degrees: float = 15.0,
) -> tuple[float | None, float]:
    reference_signature = annular_signature(reference, reference_circle)
    candidate_signature = annular_signature(candidate, candidate_circle)
    if not np.any(reference_signature) or not np.any(candidate_signature):
        return None, 0.0

    correlation = np.fft.ifft(
        np.fft.fft(reference_signature) * np.conj(np.fft.fft(candidate_signature))
    ).real
    sample_count = len(correlation)
    degrees = np.arange(sample_count, dtype=np.float32) * (360.0 / sample_count)
    degrees = np.where(degrees > 180.0, degrees - 360.0, degrees)
    valid = np.abs(degrees) <= search_limit_degrees
    if not np.any(valid):
        return None, 0.0
    valid_indices = np.where(valid)[0]
    best_index = valid_indices[int(np.argmax(correlation[valid]))]
    return float(degrees[best_index]), float(correlation[best_index] / sample_count)


def circle_similarity_matrix(
    reference_circle: tuple[float, float, float],
    candidate_circle: tuple[float, float, float],
    rotation_degrees: float,
) -> np.ndarray:
    reference_x, reference_y, reference_radius = reference_circle
    candidate_x, candidate_y, candidate_radius = candidate_circle
    scale = reference_radius / max(1e-6, candidate_radius)
    matrix = cv2.getRotationMatrix2D(
        (candidate_x, candidate_y),
        rotation_degrees,
        scale,
    ).astype(np.float64)
    mapped_center = matrix @ np.array([candidate_x, candidate_y, 1.0], dtype=np.float64)
    matrix[0, 2] += reference_x - mapped_center[0]
    matrix[1, 2] += reference_y - mapped_center[1]
    return matrix


def gradient_geometry_image(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    gradient_x = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gradient_y = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    magnitude = cv2.magnitude(gradient_x, gradient_y)
    magnitude = cv2.GaussianBlur(magnitude, (0, 0), 1.0)
    return magnitude / (float(magnitude.max()) + 1e-6)


def affine_decomposition(matrix: np.ndarray) -> tuple[float, float, float, float]:
    linear = matrix[:, :2].astype(np.float64)
    u, singular_values, vh = np.linalg.svd(linear)
    rotation_matrix = u @ vh
    if np.linalg.det(rotation_matrix) < 0:
        vh[-1, :] *= -1
        singular_values[-1] *= -1
        rotation_matrix = u @ vh
    rotation = math.degrees(math.atan2(rotation_matrix[1, 0], rotation_matrix[0, 0]))
    sx = float(abs(singular_values[0]))
    sy = float(abs(singular_values[1]))
    anisotropy = max(sx, sy) / max(1e-6, min(sx, sy))
    return rotation, sx, sy, anisotropy


def refine_watch_alignment_ecc(
    reference: np.ndarray,
    candidate: np.ndarray,
    base_matrix: np.ndarray,
    reference_circle: tuple[float, float, float],
) -> tuple[np.ndarray, dict]:
    """Refine residual position, scale and tiny rotation after watch-specific alignment.

    The circle and hour-marker stages solve the main geometry. This pass only makes
    small local corrections inside the watch annulus, which helps equalise the
    visible crystal size while rejecting implausible affine warps.
    """
    h, w = reference.shape[:2]
    aligned = cv2.warpAffine(
        candidate,
        base_matrix,
        (w, h),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(18, 18, 18),
    )
    reference_geometry = gradient_geometry_image(reference)
    aligned_geometry = gradient_geometry_image(aligned)

    center_x, center_y, radius = reference_circle
    yy, xx = np.ogrid[:h, :w]
    distance = np.sqrt((xx - center_x) ** 2 + (yy - center_y) ** 2)
    annulus_mask = (
        (distance > radius * 0.56)
        & (distance < radius * 1.12)
    ).astype(np.uint8) * 255

    warp = np.eye(2, 3, dtype=np.float32)
    criteria = (
        cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT,
        600,
        1e-7,
    )
    try:
        score, inverse_warp = cv2.findTransformECC(
            reference_geometry,
            aligned_geometry,
            warp,
            cv2.MOTION_AFFINE,
            criteria,
            inputMask=annulus_mask,
            gaussFiltSize=5,
        )
        forward_correction = cv2.invertAffineTransform(inverse_warp).astype(np.float64)
        correction_rotation, sx, sy, anisotropy = affine_decomposition(forward_correction)
        correction_translation = math.hypot(
            float(forward_correction[0, 2]),
            float(forward_correction[1, 2]),
        )
        average_scale = (sx + sy) / 2.0
        plausible = (
            float(score) >= 0.18
            and correction_translation <= 0.10 * min(h, w)
            and abs(correction_rotation) <= 2.0
            and 0.97 <= average_scale <= 1.03
            and anisotropy <= 1.03
        )
        if plausible:
            refined = combine_affine(forward_correction, base_matrix)
        else:
            refined = base_matrix
        return refined, {
            "ecc_score": round(float(score), 3),
            "ecc_rotation_correction_deg": round(correction_rotation, 3),
            "ecc_scale_correction_x": round(sx, 5),
            "ecc_scale_correction_y": round(sy, 5),
            "ecc_scale_correction_avg": round(average_scale, 5),
            "ecc_affine_anisotropy": round(anisotropy, 5),
            "ecc_translation_correction_px": round(correction_translation, 2),
            "ecc_applied": bool(plausible),
        }
    except cv2.error:
        return base_matrix, {
            "ecc_score": 0.0,
            "ecc_rotation_correction_deg": 0.0,
            "ecc_scale_correction_x": 1.0,
            "ecc_scale_correction_y": 1.0,
            "ecc_scale_correction_avg": 1.0,
            "ecc_affine_anisotropy": 1.0,
            "ecc_translation_correction_px": 0.0,
            "ecc_applied": False,
        }



def logo_geometry_image(image: np.ndarray) -> np.ndarray:
    """Enhance the crown and ROLEX text while reducing lighting differences."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.2, tileGridSize=(6, 6)).apply(gray)
    gradient_x = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gradient_y = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    magnitude = cv2.magnitude(gradient_x, gradient_y)
    magnitude = magnitude / (float(magnitude.max()) + 1e-6)

    # White dial printing is small and bright. A horizontal top-hat filter gives
    # it more influence than the hands and broad dial reflections.
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (13, 5))
    top_hat = cv2.morphologyEx(gray, cv2.MORPH_TOPHAT, kernel).astype(np.float32) / 255.0
    combined = magnitude * 0.72 + top_hat * 0.28
    return cv2.GaussianBlur(combined, (0, 0), 0.8)


def refine_logo_text_alignment(
    reference: np.ndarray,
    candidate: np.ndarray,
    base_matrix: np.ndarray,
    reference_circle: tuple[float, float, float],
) -> tuple[np.ndarray, dict]:
    """Apply a final tiny rigid correction using the crown and ROLEX text.

    Crystal, bezel and marker geometry remain the main alignment anchors. This
    pass searches only the upper-centre dial-printing region and applies a
    translation-only correction of a few pixels. Rotation and scale remain
    controlled by the crystal and hour-marker stages.
    """
    h, w = reference.shape[:2]
    aligned = cv2.warpAffine(
        candidate,
        base_matrix,
        (w, h),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(18, 18, 18),
    )

    center_x, center_y, radius = reference_circle
    x0 = max(0, int(round(center_x - radius * 0.45)))
    x1 = min(w, int(round(center_x + radius * 0.45)))
    y0 = max(0, int(round(center_y - radius * 0.43)))
    y1 = min(h, int(round(center_y - radius * 0.05)))

    if x1 - x0 < 80 or y1 - y0 < 45:
        return base_matrix, {
            "logo_lock_applied": False,
            "logo_lock_reason": "logo region too small",
            "logo_lock_score": 0.0,
            "logo_lock_rotation_deg": 0.0,
            "logo_lock_translation_px": 0.0,
        }

    reference_logo = logo_geometry_image(reference[y0:y1, x0:x1])
    aligned_logo = logo_geometry_image(aligned[y0:y1, x0:x1])
    warp = np.eye(2, 3, dtype=np.float32)
    criteria = (
        cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT,
        500,
        1e-7,
    )

    try:
        score, inverse_warp = cv2.findTransformECC(
            reference_logo,
            aligned_logo,
            warp,
            cv2.MOTION_TRANSLATION,
            criteria,
            inputMask=None,
            gaussFiltSize=5,
        )
        roi_correction = cv2.invertAffineTransform(inverse_warp).astype(np.float64)
        rotation = 0.0
        translation = math.hypot(float(roi_correction[0, 2]), float(roi_correction[1, 2]))

        # Convert the correction from ROI coordinates to the full crop.
        to_roi = np.array([[1.0, 0.0, -x0], [0.0, 1.0, -y0]], dtype=np.float64)
        from_roi = np.array([[1.0, 0.0, x0], [0.0, 1.0, y0]], dtype=np.float64)
        full_correction = combine_affine(from_roi, combine_affine(roi_correction, to_roi))

        max_translation = min(18.0, max(10.0, radius * 0.105))
        plausible = (
            float(score) >= 0.34
            and translation <= max_translation
            and abs(rotation) <= 0.01
        )
        refined = combine_affine(full_correction, base_matrix) if plausible else base_matrix
        return refined, {
            "logo_lock_applied": bool(plausible),
            "logo_lock_score": round(float(score), 3),
            "logo_lock_rotation_deg": round(float(rotation), 3),
            "logo_lock_translation_px": round(float(translation), 2),
            "logo_lock_region": [x0, y0, x1, y1],
            "logo_lock_reason": "applied" if plausible else "correction outside safety bounds",
        }
    except cv2.error:
        return base_matrix, {
            "logo_lock_applied": False,
            "logo_lock_reason": "logo refinement failed",
            "logo_lock_score": 0.0,
            "logo_lock_rotation_deg": 0.0,
            "logo_lock_translation_px": 0.0,
        }

def detect_dial_ellipse(
    image: np.ndarray,
    circle: tuple[float, float, float],
) -> tuple[float, float, float, float, float] | None:
    """Fit an ellipse to the watch crystal edge by sampling radial edge strength.

    A tilted camera makes the circular dial appear as an ellipse. We sample at
    72 angles around the expected circumference, record the radius of the
    strongest edge at each angle, then fit an ellipse to those points.

    Returns (cx, cy, semi_major, semi_minor, angle_deg) — the inclination of
    the major axis measured clockwise from horizontal — or None if detection
    fails or the fit looks implausible.
    """
    cx, cy, r = circle
    h, w = image.shape[:2]

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (7, 7), 2.0)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    mag = cv2.magnitude(gx, gy)
    edge_threshold = float(np.percentile(mag, 70))

    n_angles = 72
    edge_pts: list[tuple[float, float]] = []
    radii = np.arange(r * 0.75, r * 1.35, 0.5)

    for i in range(n_angles):
        a = 2.0 * math.pi * i / n_angles
        cos_a, sin_a = math.cos(a), math.sin(a)
        best_v, best_px, best_py = 0.0, cx + cos_a * r, cy + sin_a * r
        for radius in radii:
            px = cx + cos_a * radius
            py = cy + sin_a * radius
            ix, iy = int(px), int(py)
            if not (0 <= ix < w - 1 and 0 <= iy < h - 1):
                continue
            dx, dy = px - ix, py - iy
            v = (mag[iy,   ix  ] * (1 - dx) * (1 - dy)
               + mag[iy,   ix+1] * dx        * (1 - dy)
               + mag[iy+1, ix  ] * (1 - dx) * dy
               + mag[iy+1, ix+1] * dx        * dy)
            if v > best_v:
                best_v, best_px, best_py = v, px, py
        if best_v > edge_threshold:
            edge_pts.append((best_px, best_py))

    if len(edge_pts) < 24:
        return None

    pts = np.array(edge_pts, dtype=np.float32).reshape(-1, 1, 2)
    try:
        (ex, ey), (ew, eh), angle = cv2.fitEllipse(pts)
    except cv2.error:
        return None

    semi_major = max(ew, eh) / 2.0
    semi_minor = min(ew, eh) / 2.0

    if not (r * 0.70 < semi_major < r * 1.30):
        return None
    if semi_minor < r * 0.40:
        return None

    # OpenCV fitEllipse returns the angle of the *first* axis (ew direction).
    # Normalise so angle always refers to the major axis.
    if ew < eh:
        angle = (angle + 90.0) % 180.0

    return float(ex), float(ey), float(semi_major), float(semi_minor), float(angle)


def ellipse_to_circle_affine(
    ellipse: tuple[float, float, float, float, float],
    target_center: tuple[float, float],
    target_radius: float,
) -> np.ndarray:
    """Return a 2×3 affine matrix that maps the ellipse to a circle.

    Steps (all composed as 3×3 then truncated to 2×3):
      1. Translate ellipse centre to origin
      2. Rotate so the major axis aligns with the x-axis
      3. Scale x by target_radius/semi_major, y by target_radius/semi_minor
         — this turns the ellipse into a circle of radius target_radius
      4. Rotate back
      5. Translate to target_center
    """
    cx, cy, semi_major, semi_minor, angle_deg = ellipse
    theta = math.radians(angle_deg)
    cos_t, sin_t = math.cos(theta), math.sin(theta)

    T1 = np.array([[1, 0, -cx], [0, 1, -cy], [0, 0, 1]], dtype=np.float64)
    R1 = np.array([[ cos_t, sin_t, 0], [-sin_t, cos_t, 0], [0, 0, 1]], dtype=np.float64)
    sx = target_radius / max(semi_major, 1.0)
    sy = target_radius / max(semi_minor, 1.0)
    S  = np.array([[sx, 0, 0], [0, sy, 0], [0, 0, 1]], dtype=np.float64)
    R2 = np.array([[cos_t, -sin_t, 0], [sin_t, cos_t, 0], [0, 0, 1]], dtype=np.float64)
    T2 = np.array([[1, 0, target_center[0]], [0, 1, target_center[1]], [0, 0, 1]], dtype=np.float64)

    M3 = T2 @ R2 @ S @ R1 @ T1
    return M3[:2, :]


def perspective_dewarp_candidate(
    candidate: np.ndarray,
    circle: tuple[float, float, float],
) -> tuple[np.ndarray, np.ndarray, dict]:
    """Detect any perspective tilt in the candidate and apply an affine de-warp.

    A camera that isn't perfectly square-on to the watch face makes the circular
    dial appear elliptical. We detect the ellipse, compute an affine squeeze that
    restores it to circular, and apply it to the candidate image.

    The returned 2×3 matrix maps original-candidate coordinates to dewarped
    coordinates; compose it with the alignment matrix to get a single transform
    from the original candidate to the reference frame.

    Only applies when the axis ratio (minor/major) is between 0.78 and 0.95
    — roughly 18–39° of tilt. Milder tilt doesn't need correction; more extreme
    tilt suggests detection has gone wrong, so we skip it.
    """
    identity = np.array([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]], dtype=np.float64)
    no_op_metrics: dict = {"perspective_dewarp_applied": False, "perspective_axis_ratio": 1.0}

    ellipse = detect_dial_ellipse(candidate, circle)
    if ellipse is None:
        return candidate, identity, {**no_op_metrics, "perspective_dewarp_reason": "ellipse not detected"}

    _, _, semi_major, semi_minor, angle_deg = ellipse
    axis_ratio = semi_minor / max(semi_major, 1.0)

    if axis_ratio >= 0.95:
        return candidate, identity, {**no_op_metrics, "perspective_dewarp_reason": "tilt negligible"}
    if axis_ratio < 0.78:
        return candidate, identity, {**no_op_metrics, "perspective_dewarp_reason": "tilt too extreme for reliable correction"}

    dewarp = ellipse_to_circle_affine(
        ellipse,
        target_center=(circle[0], circle[1]),
        target_radius=circle[2],
    )
    h, w = candidate.shape[:2]
    dewarped = cv2.warpAffine(
        candidate, dewarp, (w, h),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(18, 18, 18),
    )
    squeeze = semi_major / max(semi_minor, 1.0)
    metrics: dict = {
        "perspective_dewarp_applied": True,
        "perspective_axis_ratio": round(axis_ratio, 4),
        "perspective_squeeze_factor": round(squeeze, 4),
        "perspective_tilt_angle_deg": round(angle_deg, 1),
        "perspective_semi_major_px": round(semi_major, 1),
        "perspective_semi_minor_px": round(semi_minor, 1),
    }
    return dewarped, dewarp, metrics


def orb_auto_align(reference: np.ndarray, candidate: np.ndarray) -> tuple[np.ndarray, dict]:
    """Legacy feature-matching fallback used when a watch circle is unavailable."""
    ref_gray = cv2.cvtColor(reference, cv2.COLOR_BGR2GRAY)
    cand_gray = cv2.cvtColor(candidate, cv2.COLOR_BGR2GRAY)

    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    ref_gray = clahe.apply(ref_gray)
    cand_gray = clahe.apply(cand_gray)

    orb = cv2.ORB_create(
        nfeatures=8000,
        scaleFactor=1.2,
        nlevels=8,
        edgeThreshold=21,
        fastThreshold=8,
    )
    ref_kp, ref_desc = orb.detectAndCompute(ref_gray, center_mask(ref_gray.shape))
    cand_kp, cand_desc = orb.detectAndCompute(cand_gray, center_mask(cand_gray.shape))

    if ref_desc is None or cand_desc is None or len(ref_kp) < 12 or len(cand_kp) < 12:
        raise HTTPException(status_code=422, detail="Not enough visible watch detail for automatic alignment.")

    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    pairs = matcher.knnMatch(cand_desc, ref_desc, k=2)
    good = [m for m, n in pairs if m.distance < 0.76 * n.distance]

    if len(good) < 10:
        cross = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        good = sorted(cross.match(cand_desc, ref_desc), key=lambda match: match.distance)[:100]

    if len(good) < 8:
        raise HTTPException(status_code=422, detail="Automatic alignment could not find enough matching details.")

    cand_points = np.float32([cand_kp[m.queryIdx].pt for m in good])
    ref_points = np.float32([ref_kp[m.trainIdx].pt for m in good])

    matrix, inliers = cv2.estimateAffinePartial2D(
        cand_points,
        ref_points,
        method=cv2.RANSAC,
        ransacReprojThreshold=4.0,
        maxIters=5000,
        confidence=0.995,
        refineIters=25,
    )

    if matrix is None or inliers is None:
        raise HTTPException(status_code=422, detail="Automatic alignment failed to estimate a stable transform.")

    inlier_mask = inliers.ravel().astype(bool)
    inlier_count = int(inlier_mask.sum())
    if inlier_count < 6:
        raise HTTPException(status_code=422, detail="Automatic alignment confidence is too low. Use manual adjustment.")

    projected = cv2.transform(cand_points.reshape(-1, 1, 2), matrix).reshape(-1, 2)
    errors = np.linalg.norm(projected - ref_points, axis=1)
    median_error = float(np.median(errors[inlier_mask])) if inlier_mask.any() else float("inf")

    a, b = float(matrix[0, 0]), float(matrix[0, 1])
    scale = math.sqrt(a * a + b * b)
    rotation = math.degrees(math.atan2(b, a))
    inlier_ratio = inlier_count / max(1, len(good))

    score = max(0.0, min(1.0, (inlier_ratio * 0.65) + (min(inlier_count, 35) / 35 * 0.25) + (max(0.0, 1.0 - median_error / 8.0) * 0.10)))
    confidence = "high" if score >= 0.68 else "medium" if score >= 0.42 else "low"

    metrics = {
        "alignment_method": "ORB feature fallback",
        "matches": len(good),
        "inliers": inlier_count,
        "inlier_ratio": round(inlier_ratio, 3),
        "median_error_px": round(median_error, 2),
        "detected_scale": round(scale, 5),
        "detected_rotation_deg": round(rotation, 3),
        "confidence": confidence,
        "confidence_score": round(score, 3),
    }
    return matrix.astype(np.float64), metrics


def auto_align(reference: np.ndarray, candidate: np.ndarray) -> tuple[np.ndarray, dict]:
    """Watch-aware automatic alignment with crop, crystal sizing and constrained refinement."""
    reference_circle_initial = detect_watch_circle(reference)
    candidate_circle_initial = detect_watch_circle(candidate)
    if reference_circle_initial is None or candidate_circle_initial is None:
        return orb_auto_align(reference, candidate)

    # Perspective pre-correction: if the candidate was shot at a slight angle the
    # circular dial appears elliptical. Squeezing it back to circular before the
    # rest of the pipeline improves every subsequent alignment step.
    candidate, dewarp_matrix, dewarp_metrics = perspective_dewarp_candidate(
        candidate, candidate_circle_initial
    )
    # Re-detect the circle on the dewarped candidate so the rest of the pipeline
    # works with the corrected geometry. Fall back to the original if it fails.
    if dewarp_metrics["perspective_dewarp_applied"]:
        redetected = detect_watch_circle(candidate)
        if redetected is not None:
            candidate_circle_initial = redetected

    reference_crop, reference_offset = auto_crop_around_watch(reference, reference_circle_initial)
    candidate_crop, candidate_offset = auto_crop_around_watch(candidate, candidate_circle_initial)

    reference_circle = refine_crystal_circle(
        reference_crop,
        (
            reference_circle_initial[0] - reference_offset[0],
            reference_circle_initial[1] - reference_offset[1],
            reference_circle_initial[2],
        ),
    )
    candidate_circle = refine_crystal_circle(
        candidate_crop,
        (
            candidate_circle_initial[0] - candidate_offset[0],
            candidate_circle_initial[1] - candidate_offset[1],
            candidate_circle_initial[2],
        ),
    )

    reference_marker_angle, reference_marker_count = estimate_hour_marker_orientation(
        reference_crop, reference_circle
    )
    candidate_marker_angle, candidate_marker_count = estimate_hour_marker_orientation(
        candidate_crop, candidate_circle
    )

    marker_rotation: float | None = None
    if reference_marker_angle is not None and candidate_marker_angle is not None:
        marker_rotation = (
            (candidate_marker_angle - reference_marker_angle + 180.0) % 360.0
        ) - 180.0

    polar_rotation, polar_correlation = estimate_polar_rotation(
        reference_crop,
        candidate_crop,
        reference_circle,
        candidate_circle,
    )

    if marker_rotation is not None and polar_rotation is not None:
        disagreement = abs(((marker_rotation - polar_rotation + 180.0) % 360.0) - 180.0)
        if disagreement <= 2.0:
            rotation = marker_rotation * 0.70 + polar_rotation * 0.30
        else:
            rotation = marker_rotation
    elif marker_rotation is not None:
        rotation = marker_rotation
    elif polar_rotation is not None:
        rotation = polar_rotation
    else:
        rotation = 0.0

    rotation = float(np.clip(rotation, -15.0, 15.0))
    initial_matrix_crop = circle_similarity_matrix(reference_circle, candidate_circle, rotation)
    refined_matrix_crop, ecc_metrics = refine_watch_alignment_ecc(
        reference_crop,
        candidate_crop,
        initial_matrix_crop,
        reference_circle,
    )
    refined_matrix_crop, logo_metrics = refine_logo_text_alignment(
        reference_crop,
        candidate_crop,
        refined_matrix_crop,
        reference_circle,
    )

    to_candidate_crop = translate_affine(-candidate_offset[0], -candidate_offset[1])
    from_reference_crop = translate_affine(reference_offset[0], reference_offset[1])
    # Compose: original candidate → dewarp → crop → align → uncrop → reference
    matrix = combine_affine(
        from_reference_crop,
        combine_affine(refined_matrix_crop, combine_affine(to_candidate_crop, dewarp_matrix)),
    )

    a, b = float(matrix[0, 0]), float(matrix[0, 1])
    scale = math.sqrt(a * a + b * b)
    final_rotation = math.degrees(math.atan2(b, a))

    marker_quality = min(reference_marker_count, candidate_marker_count) / 12.0
    polar_quality = max(0.0, min(1.0, polar_correlation / 0.55))
    ecc_quality = max(0.0, min(1.0, float(ecc_metrics["ecc_score"]) / 0.55))
    score = max(0.0, min(1.0, marker_quality * 0.40 + polar_quality * 0.20 + ecc_quality * 0.40))
    confidence = "high" if score >= 0.72 else "medium" if score >= 0.48 else "low"

    metrics = {
        "alignment_method": "crystal circle + hour markers + polar rotation + affine ECC + ROLEX logo lock",
        "matches": min(reference_marker_count, candidate_marker_count),
        "inliers": min(reference_marker_count, candidate_marker_count),
        "inlier_ratio": round(marker_quality, 3),
        "median_error_px": 0.0,
        "detected_scale": round(scale, 5),
        "detected_rotation_deg": round(final_rotation, 3),
        "initial_marker_rotation_deg": round(marker_rotation, 3) if marker_rotation is not None else None,
        "initial_polar_rotation_deg": round(polar_rotation, 3) if polar_rotation is not None else None,
        "polar_correlation": round(polar_correlation, 3),
        "confidence": confidence,
        "confidence_score": round(score, 3),
        "reference_circle": [round(value, 2) for value in reference_circle_initial],
        "candidate_circle": [round(value, 2) for value in candidate_circle_initial],
        "reference_crystal_circle": [round(float(reference_circle[0] + reference_offset[0]), 2), round(float(reference_circle[1] + reference_offset[1]), 2), round(float(reference_circle[2]), 2)],
        "candidate_crystal_circle": [round(float(candidate_circle[0] + candidate_offset[0]), 2), round(float(candidate_circle[1] + candidate_offset[1]), 2), round(float(candidate_circle[2]), 2)],
        "reference_crop_offset": [int(reference_offset[0]), int(reference_offset[1])],
        "candidate_crop_offset": [int(candidate_offset[0]), int(candidate_offset[1])],
        **ecc_metrics,
        **logo_metrics,
        **dewarp_metrics,
    }
    return matrix.astype(np.float64), metrics

def adjustment_matrix(width: int, height: int, rotation: float, scale: float, x: float, y: float) -> np.ndarray:
    matrix = cv2.getRotationMatrix2D((width / 2.0, height / 2.0), rotation, scale).astype(np.float64)
    matrix[0, 2] += x
    matrix[1, 2] += y
    return matrix


def combine_affine(after: np.ndarray, before: np.ndarray) -> np.ndarray:
    after3 = np.vstack([after, [0.0, 0.0, 1.0]])
    before3 = np.vstack([before, [0.0, 0.0, 1.0]])
    return (after3 @ before3)[:2]




def central_watch_mask(shape: tuple[int, int], valid_mask: np.ndarray) -> np.ndarray:
    """Return a dial-focused mask for robust appearance statistics.

    Geometry and colour matching are intentionally separated. The mask focuses on
    the central watch area and ignores warped padding and most background pixels.
    """
    h, w = shape
    mask = np.zeros((h, w), dtype=np.uint8)
    radius = max(20, int(min(h, w) * 0.36))
    cv2.circle(mask, (w // 2, h // 2), radius, 255, -1)
    mask = cv2.bitwise_and(mask, valid_mask)
    return mask


def robust_percentiles(channel: np.ndarray, mask: np.ndarray) -> tuple[float, float, float]:
    values = channel[mask > 0].astype(np.float32)
    if values.size < 100:
        return 0.0, 127.5, 255.0
    p10, p50, p90 = np.percentile(values, [10, 50, 90])
    return float(p10), float(p50), float(p90)


def piecewise_tone_map(channel: np.ndarray, source_points: tuple[float, float, float], target_points: tuple[float, float, float]) -> np.ndarray:
    """Map shadows, midpoint and highlights while preserving local image detail."""
    s10, s50, s90 = source_points
    t10, t50, t90 = target_points
    source = channel.astype(np.float32)
    output = np.empty_like(source)

    lower_denominator = max(8.0, s50 - s10)
    upper_denominator = max(8.0, s90 - s50)
    lower_scale = np.clip((t50 - t10) / lower_denominator, 0.45, 2.20)
    upper_scale = np.clip((t90 - t50) / upper_denominator, 0.45, 2.20)

    lower = source <= s50
    output[lower] = t50 + (source[lower] - s50) * lower_scale
    output[~lower] = t50 + (source[~lower] - s50) * upper_scale
    return np.clip(output, 0, 255)


def match_appearance(reference: np.ndarray, aligned: np.ndarray, valid_mask: np.ndarray, strength: float) -> tuple[np.ndarray, dict]:
    """Match candidate exposure, contrast and colour to the reference.

    The transform is derived from a dial-focused region in LAB colour space. It
    adjusts luminance with a robust three-point tone curve and neutral colour
    channels with median/spread transfer. The result is blended with the original
    so the user can control strength and inspect the unmodified source at any time.
    """
    strength = float(np.clip(strength, 0.0, 1.0))
    sample_mask = central_watch_mask(reference.shape[:2], valid_mask)
    sample_count = int(np.count_nonzero(sample_mask))
    if sample_count < 500:
        return aligned.copy(), {
            "enabled": True,
            "strength": round(strength, 3),
            "sample_pixels": sample_count,
            "status": "insufficient overlap",
        }

    reference_lab = cv2.cvtColor(reference, cv2.COLOR_BGR2LAB).astype(np.float32)
    aligned_lab = cv2.cvtColor(aligned, cv2.COLOR_BGR2LAB).astype(np.float32)
    matched_lab = aligned_lab.copy()

    # Luminance: robust shadow/mid/highlight transfer.
    ref_l_points = robust_percentiles(reference_lab[:, :, 0], sample_mask)
    cand_l_points = robust_percentiles(aligned_lab[:, :, 0], sample_mask)
    matched_lab[:, :, 0] = piecewise_tone_map(aligned_lab[:, :, 0], cand_l_points, ref_l_points)

    # Colour: robust median and percentile-spread transfer in LAB a/b channels.
    for channel_index in (1, 2):
        ref_values = reference_lab[:, :, channel_index][sample_mask > 0]
        cand_values = aligned_lab[:, :, channel_index][sample_mask > 0]
        ref_median = float(np.median(ref_values))
        cand_median = float(np.median(cand_values))
        ref_p10, ref_p90 = np.percentile(ref_values, [10, 90])
        cand_p10, cand_p90 = np.percentile(cand_values, [10, 90])
        ref_spread = max(4.0, float(ref_p90 - ref_p10))
        cand_spread = max(4.0, float(cand_p90 - cand_p10))
        colour_scale = float(np.clip(ref_spread / cand_spread, 0.55, 1.80))
        matched_lab[:, :, channel_index] = np.clip(
            (aligned_lab[:, :, channel_index] - cand_median) * colour_scale + ref_median,
            0,
            255,
        )

    matched = cv2.cvtColor(np.clip(matched_lab, 0, 255).astype(np.uint8), cv2.COLOR_LAB2BGR)

    alpha = (valid_mask.astype(np.float32) / 255.0 * strength)[:, :, None]
    result = np.clip(aligned.astype(np.float32) * (1.0 - alpha) + matched.astype(np.float32) * alpha, 0, 255).astype(np.uint8)

    # Report a simple LAB-distance improvement over the watch sample region.
    before = cv2.cvtColor(aligned, cv2.COLOR_BGR2LAB).astype(np.float32)
    after = cv2.cvtColor(result, cv2.COLOR_BGR2LAB).astype(np.float32)
    target = reference_lab
    before_delta = np.linalg.norm(before - target, axis=2)[sample_mask > 0]
    after_delta = np.linalg.norm(after - target, axis=2)[sample_mask > 0]
    before_mean = float(np.mean(before_delta))
    after_mean = float(np.mean(after_delta))
    improvement = 0.0 if before_mean <= 0.001 else max(0.0, 1.0 - after_mean / before_mean)

    metrics = {
        "enabled": True,
        "strength": round(strength, 3),
        "sample_pixels": sample_count,
        "status": "matched",
        "mean_lab_distance_before": round(before_mean, 2),
        "mean_lab_distance_after": round(after_mean, 2),
        "appearance_improvement": round(improvement, 3),
        "reference_luminance_points": [round(value, 1) for value in ref_l_points],
        "candidate_luminance_points": [round(value, 1) for value in cand_l_points],
    }
    return result, metrics


def load_session(session_id: str) -> tuple[Path, np.ndarray, np.ndarray, np.ndarray, dict]:
    if not session_id or any(ch not in "0123456789abcdef-" for ch in session_id.lower()):
        raise HTTPException(status_code=400, detail="Invalid session ID.")
    folder = SESSIONS_DIR / session_id
    if not folder.is_dir():
        raise HTTPException(status_code=404, detail="Session not found or expired.")

    reference = cv2.imread(str(folder / "reference.png"))
    candidate = cv2.imread(str(folder / "candidate.png"))
    if reference is None or candidate is None:
        raise HTTPException(status_code=500, detail="Session images are unavailable.")
    matrix = np.load(folder / "base_transform.npy")
    metrics = json.loads((folder / "metrics.json").read_text())
    folder.touch()
    return folder, reference, candidate, matrix, metrics


def metric_circle(metrics: dict | None, key: str) -> tuple[float, float, float] | None:
    if not metrics:
        return None
    value = metrics.get(key)
    if not isinstance(value, (list, tuple)) or len(value) != 3:
        return None
    try:
        circle = tuple(float(item) for item in value)
    except (TypeError, ValueError):
        return None
    if circle[2] <= 5:
        return None
    return circle


def comparison_geometry(
    reference: np.ndarray,
    candidate: np.ndarray,
    metrics: dict | None,
) -> tuple[tuple[float, float, float], tuple[float, float, float] | None, float, int, np.ndarray]:
    """Choose a common high-resolution comparison canvas.

    The old renderer inherited its pixel density from the reference image. A
    small web reference therefore forced a large QC photo to be resampled down.
    This renderer instead uses the sharper watch as the pixel-density target,
    then maps both sources directly into one square canvas.
    """
    ref_circle = metric_circle(metrics, "reference_crystal_circle") or detect_refined_circle_full(reference)
    if ref_circle is None:
        h, w = reference.shape[:2]
        ref_circle = (w / 2.0, h / 2.0, min(h, w) * 0.30)

    cand_circle = metric_circle(metrics, "candidate_crystal_circle") or detect_refined_circle_full(candidate)
    source_target_radius = max(float(ref_circle[2]), float(cand_circle[2]) if cand_circle else 0.0)
    desired_size = int(round(source_target_radius * COMPARISON_MARGIN_RATIO * 2.0))
    output_size = max(MIN_COMPARISON_SIZE, min(MAX_COMPARISON_SIZE, desired_size))
    if output_size % 2:
        output_size += 1

    target_radius = output_size / (COMPARISON_MARGIN_RATIO * 2.0)
    render_scale = target_radius / max(1e-6, float(ref_circle[2]))
    cx, cy, _ = ref_circle
    reference_to_canvas = np.array(
        [
            [render_scale, 0.0, output_size / 2.0 - render_scale * cx],
            [0.0, render_scale, output_size / 2.0 - render_scale * cy],
        ],
        dtype=np.float64,
    )
    target_circle = (output_size / 2.0, output_size / 2.0, target_radius)
    return target_circle, cand_circle, render_scale, output_size, reference_to_canvas


def circle_correction_matrix(
    target_circle: tuple[float, float, float],
    preliminary_aligned: np.ndarray,
) -> tuple[np.ndarray, dict]:
    aligned_circle = detect_refined_circle_full(preliminary_aligned)
    identity = np.array([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]], dtype=np.float64)
    if aligned_circle is None:
        return identity, {
            "post_circle_correction_applied": False,
            "post_circle_correction_reason": "aligned circle not found",
        }

    correction = circle_similarity_matrix(target_circle, aligned_circle, 0.0)
    _, sx, sy, anisotropy = affine_decomposition(correction)
    average_scale = (sx + sy) / 2.0
    translation = math.hypot(float(correction[0, 2]), float(correction[1, 2]))
    canvas_size = min(preliminary_aligned.shape[:2])
    plausible = (
        0.94 <= average_scale <= 1.06
        and anisotropy <= 1.025
        and translation <= 0.10 * canvas_size
    )
    metrics = {
        "post_circle_correction_applied": bool(plausible),
        "post_circle_scale": round(float(average_scale), 5),
        "post_circle_translation_px": round(float(translation), 2),
        "post_circle_anisotropy": round(float(anisotropy), 5),
        "aligned_circle": [round(float(v), 2) for v in aligned_circle],
        "post_circle_correction_reason": "applied" if plausible else "correction outside safety bounds",
    }
    return (correction if plausible else identity), metrics



def dial_geometry_image(image: np.ndarray, size: int = 512) -> np.ndarray:
    """Return a lighting-resistant edge map for dial-scale matching.

    The hour-marker ring and minute track are more consistent physical anchors
    than whichever crystal or bezel edge happens to be strongest in a photo.
    """
    resized = cv2.resize(image, (size, size), interpolation=cv2.INTER_AREA)
    gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    magnitude = np.log1p(cv2.magnitude(gx, gy))
    magnitude = cv2.GaussianBlur(magnitude, (0, 0), 0.65)
    magnitude -= float(magnitude.mean())
    magnitude /= float(magnitude.std()) + 1e-6
    return magnitude.astype(np.float32)


def _masked_correlation(a: np.ndarray, b: np.ndarray, mask: np.ndarray) -> float:
    selected = mask > 0
    av = a[selected].astype(np.float32)
    bv = b[selected].astype(np.float32)
    if av.size < 100:
        return 0.0
    av -= float(av.mean())
    bv -= float(bv.mean())
    denominator = float(np.linalg.norm(av) * np.linalg.norm(bv)) + 1e-9
    return float(np.dot(av, bv) / denominator)


def dial_geometry_correction_matrix(
    reference_view: np.ndarray,
    preliminary_aligned: np.ndarray,
    target_circle: tuple[float, float, float],
) -> tuple[np.ndarray, dict]:
    """Find a final scale and centre correction from the dial marker annulus.

    Earlier versions could match an outer bezel edge in one image to a crystal
    edge in the other. This pass searches scale directly on the hour-marker and
    minute-track annulus, which is the same physical region on both watches.
    """
    h, w = reference_view.shape[:2]
    identity = np.array([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]], dtype=np.float64)
    if h < 240 or w < 240 or preliminary_aligned.shape[:2] != (h, w):
        return identity, {
            "dial_geometry_lock_applied": False,
            "dial_geometry_lock_reason": "comparison canvas unavailable",
            "dial_geometry_score": 0.0,
            "dial_geometry_scale": 1.0,
            "dial_geometry_translation_px": 0.0,
        }

    search_size = min(512, h, w)
    reference_map = dial_geometry_image(reference_view, search_size)
    candidate_map = dial_geometry_image(preliminary_aligned, search_size)

    factor_x = search_size / float(w)
    factor_y = search_size / float(h)
    center = (float(target_circle[0]) * factor_x, float(target_circle[1]) * factor_y)
    yy, xx = np.indices((search_size, search_size), dtype=np.float32)
    radial = np.sqrt((xx - center[0]) ** 2 + (yy - center[1]) ** 2)
    # This excludes the hands/pinion and most of the bezel. It retains the
    # twelve hour markers, minute track and inner rehaut edges.
    mask = ((radial >= search_size * 0.145) & (radial <= search_size * 0.345)).astype(np.float32)
    if int(mask.sum()) < 1000:
        return identity, {
            "dial_geometry_lock_applied": False,
            "dial_geometry_lock_reason": "dial annulus too small",
            "dial_geometry_score": 0.0,
            "dial_geometry_scale": 1.0,
            "dial_geometry_translation_px": 0.0,
        }

    max_shift = search_size * 0.14

    def warp_map(scale: float, dx: float = 0.0, dy: float = 0.0) -> np.ndarray:
        matrix = cv2.getRotationMatrix2D(center, 0.0, float(scale)).astype(np.float32)
        matrix[0, 2] += float(dx)
        matrix[1, 2] += float(dy)
        return cv2.warpAffine(
            candidate_map,
            matrix,
            (search_size, search_size),
            flags=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_CONSTANT,
            borderValue=0.0,
        )

    def evaluate(scale: float) -> tuple[float, float, float, float]:
        scaled = warp_map(scale)
        try:
            shift, response = cv2.phaseCorrelate(
                (scaled * mask).astype(np.float32),
                (reference_map * mask).astype(np.float32),
            )
        except cv2.error:
            shift, response = (0.0, 0.0), 0.0

        best = (-1.0, 0.0, 0.0, float(response))
        # OpenCV's phase-correlation sign can appear reversed depending on which
        # image contains more padded background, so score both directions.
        for sign in (1.0, -1.0):
            dx = float(shift[0]) * sign
            dy = float(shift[1]) * sign
            if abs(dx) > max_shift or abs(dy) > max_shift:
                continue
            moved = warp_map(scale, dx, dy)
            score = _masked_correlation(reference_map, moved, mask)
            if score > best[0]:
                best = (score, dx, dy, float(response))
        return best

    best_score = -1.0
    best_scale = 1.0
    best_dx = 0.0
    best_dy = 0.0
    best_response = 0.0

    coarse_scales = np.arange(0.70, 1.501, 0.02, dtype=np.float32)
    for scale in coarse_scales:
        score, dx, dy, response = evaluate(float(scale))
        if score > best_score:
            best_score, best_scale, best_dx, best_dy, best_response = score, float(scale), dx, dy, response

    fine_start = max(0.68, best_scale - 0.035)
    fine_end = min(1.52, best_scale + 0.035)
    for scale in np.arange(fine_start, fine_end + 0.0001, 0.002, dtype=np.float32):
        score, dx, dy, response = evaluate(float(scale))
        if score > best_score:
            best_score, best_scale, best_dx, best_dy, best_response = score, float(scale), dx, dy, response

    full_dx = best_dx / max(factor_x, 1e-6)
    full_dy = best_dy / max(factor_y, 1e-6)
    translation = math.hypot(full_dx, full_dy)
    at_limit = best_scale <= 0.705 or best_scale >= 1.495
    plausible = (
        best_score >= 0.34
        and not at_limit
        and 0.70 <= best_scale <= 1.50
        and translation <= 0.14 * min(h, w)
    )

    correction = cv2.getRotationMatrix2D(
        (float(target_circle[0]), float(target_circle[1])),
        0.0,
        float(best_scale),
    ).astype(np.float64)
    correction[0, 2] += full_dx
    correction[1, 2] += full_dy

    return (correction if plausible else identity), {
        "dial_geometry_lock_applied": bool(plausible),
        "dial_geometry_lock_reason": "applied" if plausible else "dial match not reliable",
        "dial_geometry_score": round(float(best_score), 3),
        "dial_geometry_phase_response": round(float(best_response), 3),
        "dial_geometry_scale": round(float(best_scale), 5),
        "dial_geometry_translation_px": round(float(translation), 2),
    }

def render_assets(
    folder: Path,
    reference: np.ndarray,
    candidate: np.ndarray,
    base_matrix: np.ndarray,
    request: RenderRequest,
    session_metrics: dict | None = None,
) -> dict:
    target_circle, candidate_circle, render_scale, output_size, reference_to_canvas = comparison_geometry(
        reference, candidate, session_metrics
    )

    # Build the automatic alignment first. Manual controls are deliberately
    # applied later, on the final comparison canvas. In earlier versions the
    # manual rotation was fed back through the dial/logo lock, which could make
    # the lock choose a slightly different scale and caused rotation to appear
    # to resize the candidate image.
    candidate_to_canvas = combine_affine(reference_to_canvas, base_matrix)

    reference_view = cv2.warpAffine(
        reference,
        reference_to_canvas,
        (output_size, output_size),
        flags=cv2.INTER_LANCZOS4,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(245, 245, 245),
    )

    preliminary = cv2.warpAffine(
        candidate,
        candidate_to_canvas,
        (output_size, output_size),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(18, 18, 18),
    )

    if request.logo_lock:
        dial_correction, dial_metrics = dial_geometry_correction_matrix(
            reference_view, preliminary, target_circle
        )
        candidate_to_canvas = combine_affine(dial_correction, candidate_to_canvas)
        candidate_to_canvas, logo_metrics = refine_logo_text_alignment(
            reference_view, candidate, candidate_to_canvas, target_circle
        )
        circle_metrics = {
            "post_circle_correction_applied": False,
            "post_circle_correction_reason": "replaced by dial geometry lock",
        }
    else:
        dial_metrics = {
            "dial_geometry_lock_applied": False,
            "dial_geometry_lock_reason": "disabled",
            "dial_geometry_score": 0.0,
            "dial_geometry_scale": 1.0,
            "dial_geometry_translation_px": 0.0,
        }
        circle_metrics = {
            "post_circle_correction_applied": False,
            "post_circle_correction_reason": "disabled with dial lock",
        }
        logo_metrics = {
            "logo_lock_applied": False,
            "logo_lock_reason": "disabled",
            "logo_lock_score": 0.0,
            "logo_lock_rotation_deg": 0.0,
            "logo_lock_translation_px": 0.0,
        }

    # Apply each manual control once, after all automatic corrections. Rotation
    # is therefore a pure rotation when scale is 1.000, and changing rotation
    # cannot cause the dial-size or logo locks to recalculate the image scale.
    manual_adjustment = adjustment_matrix(
        output_size,
        output_size,
        request.rotation,
        request.scale,
        request.x,
        request.y,
    )
    candidate_to_canvas = combine_affine(manual_adjustment, candidate_to_canvas)

    aligned_raw = cv2.warpAffine(
        candidate,
        candidate_to_canvas,
        (output_size, output_size),
        flags=cv2.INTER_LANCZOS4,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(18, 18, 18),
    )
    candidate_mask = np.full(candidate.shape[:2], 255, dtype=np.uint8)
    valid_mask = cv2.warpAffine(
        candidate_mask,
        candidate_to_canvas,
        (output_size, output_size),
        flags=cv2.INTER_NEAREST,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=0,
    )

    if request.appearance_match:
        aligned_matched, appearance_metrics = match_appearance(
            reference_view, aligned_raw, valid_mask, request.match_strength
        )
        aligned = aligned_matched
    else:
        aligned_matched = aligned_raw.copy()
        aligned = aligned_raw
        appearance_metrics = {
            "enabled": False,
            "strength": round(request.match_strength, 3),
            "status": "disabled",
        }

    overlay = cv2.addWeighted(reference_view, 1.0 - request.opacity, aligned, request.opacity, 0.0)

    ref_gray = cv2.cvtColor(reference_view, cv2.COLOR_BGR2GRAY)
    aligned_gray = cv2.cvtColor(aligned, cv2.COLOR_BGR2GRAY)
    ref_edges = cv2.Canny(ref_gray, 70, 160)
    aligned_edges = cv2.Canny(aligned_gray, 70, 160)
    edge_view = np.full_like(reference_view, 18)
    edge_view[ref_edges > 0] = (255, 255, 0)
    edge_view[aligned_edges > 0] = (255, 0, 255)
    overlap = (ref_edges > 0) & (aligned_edges > 0)
    edge_view[overlap] = (255, 255, 255)

    diff = cv2.absdiff(reference_view, aligned)
    diff_gray = cv2.cvtColor(diff, cv2.COLOR_BGR2GRAY)
    diff_gray = cv2.GaussianBlur(diff_gray, (0, 0), 2.0)
    low, high = np.percentile(diff_gray, [25, 97])
    if high <= low:
        normalized = np.zeros_like(diff_gray)
    else:
        normalized = np.clip(
            (diff_gray.astype(np.float32) - low) * (255.0 / (high - low)), 0, 255
        ).astype(np.uint8)
    heat = cv2.applyColorMap(normalized, cv2.COLORMAP_INFERNO)
    heatmap = cv2.addWeighted(reference_view, 0.42, heat, 0.58, 0.0)

    output_files = {
        "reference": folder / "view_reference.png",
        "candidate": folder / "view_candidate.png",
        "aligned_raw": folder / "view_aligned_raw.png",
        "aligned_matched": folder / "view_aligned_matched.png",
        "aligned": folder / "view_aligned.png",
        "overlay": folder / "view_overlay.png",
        "edges": folder / "view_edges.png",
        "heatmap": folder / "view_heatmap.png",
    }
    cv2.imwrite(str(output_files["reference"]), reference_view)
    cv2.imwrite(str(output_files["candidate"]), candidate)
    cv2.imwrite(str(output_files["aligned_raw"]), aligned_raw)
    cv2.imwrite(str(output_files["aligned_matched"]), aligned_matched)
    cv2.imwrite(str(output_files["aligned"]), aligned)
    cv2.imwrite(str(output_files["overlay"]), overlay)
    cv2.imwrite(str(output_files["edges"]), edge_view)
    cv2.imwrite(str(output_files["heatmap"]), heatmap)

    np.save(folder / "current_transform.npy", candidate_to_canvas)
    render_metrics = {
        **appearance_metrics,
        **circle_metrics,
        **dial_metrics,
        **logo_metrics,
        "normalized_canvas_size": output_size,
        "render_resolution_scale": round(float(render_scale), 4),
        "single_pass_candidate_render": True,
        "source_reference_size": [int(reference.shape[1]), int(reference.shape[0])],
        "source_candidate_size": [int(candidate.shape[1]), int(candidate.shape[0])],
        "reference_circle": [round(float(v), 2) for v in target_circle],
        "candidate_source_circle": [round(float(v), 2) for v in candidate_circle] if candidate_circle else None,
    }
    return {
        "combined_transform": candidate_to_canvas,
        "aligned": aligned,
        "aligned_raw": aligned_raw,
        "aligned_matched": aligned_matched,
        "reference_view": reference_view,
        "overlay": overlay,
        "appearance_metrics": render_metrics,
        "urls": {
            name: f"/files/{folder.name}/{path.name}?v={int(time.time() * 1000)}"
            for name, path in output_files.items()
        },
    }

def add_label(image_bgr: np.ndarray, text: str) -> Image.Image:
    rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    image = Image.fromarray(rgb)
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", max(18, image.width // 35))
    except OSError:
        font = ImageFont.load_default()
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad_x, pad_y = 13, 8
    x, y = 18, 18
    draw.rounded_rectangle((x, y, x + tw + pad_x * 2, y + th + pad_y * 2), radius=12, fill=(0, 0, 0))
    draw.text((x + pad_x, y + pad_y - 1), text, fill=(255, 255, 255), font=font)
    return image


@app.get("/")
def home() -> FileResponse:
    return FileResponse(STATIC_DIR / "index.html")


@app.post("/api/align")
def align(reference: UploadFile = File(...), candidate: UploadFile = File(...)) -> dict:
    cleanup_old_sessions()
    reference_image = resize_max(read_upload(reference))
    candidate_image = resize_max(read_upload(candidate))

    matrix, metrics = auto_align(reference_image, candidate_image)

    session_id = str(uuid.uuid4())
    folder = SESSIONS_DIR / session_id
    folder.mkdir(parents=True)
    cv2.imwrite(str(folder / "reference.png"), reference_image)
    cv2.imwrite(str(folder / "candidate.png"), candidate_image)
    np.save(folder / "base_transform.npy", matrix)
    (folder / "metrics.json").write_text(json.dumps(metrics, indent=2))

    request = RenderRequest(session_id=session_id)
    rendered = render_assets(folder, reference_image, candidate_image, matrix, request, metrics)
    return {
        "session_id": session_id,
        "metrics": {**metrics, "appearance": rendered["appearance_metrics"]},
        "images": rendered["urls"],
        "reference_size": {"width": reference_image.shape[1], "height": reference_image.shape[0]},
    }


@app.post("/api/render")
def render(request: RenderRequest) -> dict:
    folder, reference, candidate, matrix, metrics = load_session(request.session_id)
    rendered = render_assets(folder, reference, candidate, matrix, request, metrics)
    return {"metrics": {**metrics, "appearance": rendered["appearance_metrics"]}, "images": rendered["urls"]}


@app.post("/api/export")
def export(request: ExportRequest) -> dict:
    folder, reference, candidate, matrix, metrics = load_session(request.session_id)
    rendered = render_assets(folder, reference, candidate, matrix, request, metrics)
    aligned = rendered["aligned"]

    reference_frame = rendered["reference_view"]
    aligned_frame = rendered["aligned"]

    if request.format == "png":
        path = folder / "watch-comparison-overlay.png"
        cv2.imwrite(str(path), rendered["overlay"])
    elif request.format == "gif":
        path = folder / "watch-comparison.gif"
        ref_frame = add_label(reference_frame, "REFERENCE")
        candidate_label = "CANDIDATE · MATCHED" if request.appearance_match else "CANDIDATE · RAW"
        cand_frame = add_label(aligned_frame, candidate_label)
        ref_frame.save(
            path,
            save_all=True,
            append_images=[cand_frame],
            duration=[request.frame_ms, request.frame_ms],
            loop=0,
            disposal=2,
        )
    else:
        path = folder / "watch-comparison.mp4"
        candidate_label = "CANDIDATE · MATCHED" if request.appearance_match else "CANDIDATE · RAW"
        frames = [np.array(add_label(reference_frame, "REFERENCE")), np.array(add_label(aligned_frame, candidate_label))] * 2
        fps = max(1, round(1000 / request.frame_ms))
        writer = imageio.get_writer(path, fps=fps, codec="libx264", macro_block_size=1, quality=8)
        for frame in frames:
            writer.append_data(frame)
        writer.close()

    return {"download_url": f"/files/{folder.name}/{path.name}?v={int(time.time() * 1000)}", "filename": path.name}
