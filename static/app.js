const state = {
  sessionId: null,
  images: null,
  mode: "blink",
  sliderPercent: 50,
  renderTimer: null,
  renderSequence: 0,
  renderController: null,
};

const $ = (id) => document.getElementById(id);
const referenceInput = $("referenceInput");
const candidateInput = $("candidateInput");
const alignButton = $("alignButton");
const statusText = $("status");
const workspace = $("workspace");
const controls = $("controls");
const viewer = $("viewer");
const referenceImage = $("referenceImage");
const alignedImage = $("alignedImage");
const singleImage = $("singleImage");
const sliderHandle = $("sliderHandle");

function updateFileState() {
  $("referenceName").textContent = referenceInput.files[0]?.name || "No file selected";
  $("candidateName").textContent = candidateInput.files[0]?.name || "No file selected";
  alignButton.disabled = !(referenceInput.files[0] && candidateInput.files[0]);
  if (!alignButton.disabled) statusText.textContent = "Ready to align.";
}
referenceInput.addEventListener("change", updateFileState);
candidateInput.addEventListener("change", updateFileState);

function setBusy(message) {
  statusText.classList.remove("error");
  statusText.textContent = message;
  alignButton.disabled = true;
}

function showError(error) {
  statusText.classList.add("error");
  statusText.textContent = error?.message || String(error);
  updateFileState();
}

async function api(url, options = {}) {
  const response = await fetch(url, options);
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok) throw new Error(body.detail || body || `Request failed: ${response.status}`);
  return body;
}

alignButton.addEventListener("click", async () => {
  const data = new FormData();
  data.append("reference", referenceInput.files[0]);
  data.append("candidate", candidateInput.files[0]);
  setBusy("Detecting watch features and estimating alignment…");
  try {
    const result = await api("/api/align", { method: "POST", body: data });
    state.sessionId = result.session_id;
    state.images = result.images;
    applyImages(result.images);
    showMetrics(result.metrics);
    workspace.classList.remove("hidden");
    controls.classList.remove("hidden");
    statusText.textContent = "Alignment complete. Fine-tune if required.";
    statusText.classList.remove("error");
    switchMode("blink");
  } catch (error) {
    showError(error);
  }
  updateFileState();
});

function applyImages(images) {
  state.images = images;
  referenceImage.src = images.reference;
  alignedImage.src = images.aligned;
  if (state.mode === "overlay") singleImage.src = images.overlay;
  if (state.mode === "edges") singleImage.src = images.edges;
  if (state.mode === "heatmap") singleImage.src = images.heatmap;
}

function showMetrics(metrics) {
  const confidence = metrics.confidence || "unknown";
  const appearance = metrics.appearance || {};
  const appearanceHtml = appearance.enabled
    ? `
      <div><strong>Appearance match:</strong> ${Math.round((appearance.strength ?? 0) * 100)}%</div>
      <div><strong>LAB distance:</strong> ${appearance.mean_lab_distance_before ?? "n/a"} → ${appearance.mean_lab_distance_after ?? "n/a"}</div>
      <div><strong>Appearance improvement:</strong> ${appearance.appearance_improvement != null ? Math.round(appearance.appearance_improvement * 100) + "%" : appearance.status || "n/a"}</div>
    `
    : `<div><strong>Appearance match:</strong> off</div>`;
  $("confidenceText").textContent = `Automatic alignment confidence: ${confidence}`;
  const rotationDetails = metrics.initial_marker_rotation_deg != null
    ? `<div><strong>Marker rotation:</strong> ${metrics.initial_marker_rotation_deg}°</div>
       <div><strong>Polar rotation:</strong> ${metrics.initial_polar_rotation_deg ?? "n/a"}°</div>`
    : "";
  const eccDetails = metrics.ecc_score != null
    ? `<div><strong>Position refinement:</strong> ECC ${metrics.ecc_score} ${metrics.ecc_applied ? "applied" : "not applied"}</div>`
    : "";
  const dialDetails = appearance.dial_geometry_lock_reason != null
    ? `<div><strong>Dial-size lock:</strong> ${appearance.dial_geometry_lock_applied ? "applied" : appearance.dial_geometry_lock_reason} ${appearance.dial_geometry_score ? `(score ${appearance.dial_geometry_score}, scale ${appearance.dial_geometry_scale}×)` : ""}</div>`
    : "";
  const logoDetails = appearance.logo_lock_reason != null
    ? `<div><strong>Logo/text lock:</strong> ${appearance.logo_lock_applied ? "applied" : appearance.logo_lock_reason} ${appearance.logo_lock_score ? `(score ${appearance.logo_lock_score}, ${appearance.logo_lock_translation_px ?? 0}px)` : ""}</div>`
    : "";
  $("metrics").innerHTML = `
    <div><strong>Method:</strong> ${metrics.alignment_method ?? "automatic"}</div>
    <div><strong>Confidence:</strong> ${confidence} (${metrics.confidence_score ?? "n/a"})</div>
    <div><strong>Geometry checks:</strong> ${metrics.inliers ?? "n/a"} / ${metrics.matches ?? "n/a"}</div>
    <div><strong>Detected rotation:</strong> ${metrics.detected_rotation_deg}°</div>
    <div><strong>Detected scale:</strong> ${metrics.detected_scale}×</div>
    ${rotationDetails}
    ${eccDetails}
    ${dialDetails}
    ${logoDetails}
    ${appearanceHtml}
  `;
}

function switchMode(mode) {
  state.mode = mode;
  document.querySelectorAll(".tab").forEach((button) => button.classList.toggle("active", button.dataset.mode === mode));
  viewer.classList.remove("blinking");
  singleImage.classList.add("hidden");
  sliderHandle.classList.add("hidden");
  referenceImage.classList.remove("hidden");
  alignedImage.classList.remove("hidden");
  alignedImage.style.clipPath = "none";
  alignedImage.style.opacity = "1";
  referenceImage.src = state.images.reference;
  alignedImage.src = state.images.aligned;

  const matched = $("appearanceMatch").checked;
  const legends = {
    blink: `Blinking between reference and aligned candidate${matched ? " with appearance matching" : ""}`,
    overlay: `Opacity blend of both images${matched ? " after appearance matching" : ""}`,
    slider: `Drag across the image to reveal the aligned candidate${matched ? " after appearance matching" : ""}`,
    edges: "Cyan = reference edges, magenta = candidate edges, white = overlap",
    heatmap: `Brighter areas indicate larger pixel differences${matched ? " after appearance matching" : ""}`,
    matchcheck: "Blinking between the raw aligned candidate and its appearance-matched version",
  };
  $("legendText").textContent = legends[mode];

  if (mode === "blink") {
    viewer.classList.add("blinking");
  } else if (mode === "matchcheck") {
    referenceImage.src = state.images.aligned_raw;
    alignedImage.src = state.images.aligned_matched;
    viewer.classList.add("blinking");
  } else if (mode === "slider") {
    sliderHandle.classList.remove("hidden");
    alignedImage.style.clipPath = `inset(0 ${100 - state.sliderPercent}% 0 0)`;
    sliderHandle.style.left = `${state.sliderPercent}%`;
  } else {
    referenceImage.classList.add("hidden");
    alignedImage.classList.add("hidden");
    singleImage.classList.remove("hidden");
    singleImage.src = state.images[mode];
  }
}

document.querySelectorAll(".tab").forEach((button) => button.addEventListener("click", () => switchMode(button.dataset.mode)));

viewer.addEventListener("pointerdown", (event) => {
  if (state.mode !== "slider") return;
  viewer.setPointerCapture(event.pointerId);
  updateSlider(event);
});
viewer.addEventListener("pointermove", (event) => {
  if (state.mode !== "slider" || !viewer.hasPointerCapture(event.pointerId)) return;
  updateSlider(event);
});
function updateSlider(event) {
  const rect = viewer.getBoundingClientRect();
  state.sliderPercent = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100));
  alignedImage.style.clipPath = `inset(0 ${100 - state.sliderPercent}% 0 0)`;
  sliderHandle.style.left = `${state.sliderPercent}%`;
}

const controlsMap = {
  rotation: ["rotationOut", (v) => `${Number(v).toFixed(2)}°`],
  scale: ["scaleOut", (v) => `${Number(v).toFixed(3)}×`],
  xOffset: ["xOut", (v) => `${Math.round(v)} px`],
  yOffset: ["yOut", (v) => `${Math.round(v)} px`],
  opacity: ["opacityOut", (v) => `${Math.round(v * 100)}%`],
  matchStrength: ["matchStrengthOut", (v) => `${Math.round(v * 100)}%`],
};

Object.entries(controlsMap).forEach(([id, [outputId, formatter]]) => {
  $(id).addEventListener("input", () => {
    $(outputId).textContent = formatter($(id).value);
    clearTimeout(state.renderTimer);
    state.renderTimer = setTimeout(renderAdjustments, 220);
  });
});

function renderPayload() {
  return {
    session_id: state.sessionId,
    rotation: Number($("rotation").value),
    scale: Number($("scale").value),
    x: Number($("xOffset").value),
    y: Number($("yOffset").value),
    opacity: Number($("opacity").value),
    appearance_match: $("appearanceMatch").checked,
    match_strength: Number($("matchStrength").value),
    logo_lock: $("logoLock").checked,
    perspective_correction: $("perspectiveCorrection").checked,
  };
}

async function renderAdjustments() {
  if (!state.sessionId) return;

  // A quick sequence of slider movements can leave several requests in flight.
  // Cancel the older request and only apply the newest response so stale renders
  // cannot make another control appear to jump backwards or change by itself.
  const sequence = ++state.renderSequence;
  state.renderController?.abort();
  const controller = new AbortController();
  state.renderController = controller;

  try {
    const result = await api("/api/render", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(renderPayload()),
      signal: controller.signal,
    });
    if (sequence !== state.renderSequence) return;
    applyImages(result.images);
    showMetrics(result.metrics);
    switchMode(state.mode);
  } catch (error) {
    if (error?.name !== "AbortError" && sequence === state.renderSequence) {
      showError(error);
    }
  } finally {
    if (sequence === state.renderSequence) state.renderController = null;
  }
}

$("applyButton").addEventListener("click", () => {
  clearTimeout(state.renderTimer);
  renderAdjustments();
});
$("resetButton").addEventListener("click", () => {
  $("rotation").value = 0;
  $("scale").value = 1;
  $("xOffset").value = 0;
  $("yOffset").value = 0;
  $("opacity").value = .5;
  $("appearanceMatch").checked = true;
  $("logoLock").checked = true;
  $("perspectiveCorrection").checked = true;
  $("matchStrength").value = .85;
  $("matchStrengthRow").classList.remove("disabled");
  Object.entries(controlsMap).forEach(([id, [outputId, formatter]]) => $(outputId).textContent = formatter($(id).value));
  renderAdjustments();
});

$("logoLock").addEventListener("change", () => {
  clearTimeout(state.renderTimer);
  state.renderTimer = setTimeout(renderAdjustments, 100);
});

$("perspectiveCorrection").addEventListener("change", () => {
  clearTimeout(state.renderTimer);
  state.renderTimer = setTimeout(renderAdjustments, 100);
});

$("appearanceMatch").addEventListener("change", () => {
  $("matchStrengthRow").classList.toggle("disabled", !$("appearanceMatch").checked);
  clearTimeout(state.renderTimer);
  state.renderTimer = setTimeout(renderAdjustments, 100);
});

document.querySelectorAll("[data-export]").forEach((button) => {
  button.addEventListener("click", async () => {
    if (!state.sessionId) return;
    const format = button.dataset.export;
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "Preparing…";
    try {
      const result = await api("/api/export", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...renderPayload(), format, frame_ms: 900 }),
      });
      const link = document.createElement("a");
      link.href = result.download_url;
      link.download = result.filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      showError(error);
    } finally {
      button.disabled = false;
      button.textContent = original;
    }
  });
});
