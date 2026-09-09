// Comparisons run independently of the request. Polling never starts new work.
(() => {
  let busy = false;
  let jobId = null;
  const originalShow = show;
  show = function(result) {
    originalShow(result);
    const rotation = result.metrics?.base_alignment?.rotation_alignment;
    if (!rotation) return;
    const note = document.createElement('div');
    note.className = rotation.confidence === 'low' ? 'notice warn' : 'notice';
    const angle = rotation.applied_correction_deg || 0;
    note.textContent = rotation.confidence === 'low'
      ? 'Automatic rotation is uncertain. Check the overlay in Manual overlay before judging differences.'
      : 'Photo rotated ' + Math.abs(angle).toFixed(2) + '° ' + (angle < 0 ? 'clockwise' : 'anticlockwise') + ' to match the reference.';
    $('plainFinding').prepend(note);
    if (rotation.applied && result.images?.overlay) switchView('overlay');
  };
  const cancel = document.createElement('button');
  cancel.textContent = 'Cancel comparison';
  cancel.hidden = true;
  $('analyse').insertAdjacentElement('afterend', cancel);
  async function request(url, options = {}) {
    const response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) });
    const body = await response.json();
    if (!response.ok) throw new Error(body.detail || 'Comparison request failed.');
    return body;
  }
  cancel.onclick = async () => {
    if (!jobId) return;
    cancel.disabled = true;
    try { await request('/api/v1/comparison-jobs/' + jobId, { method: 'DELETE' }); }
    catch (error) { $('status').textContent = error.message; }
    finally { cancel.disabled = false; }
  };
  $('analyse').onclick = async () => {
    if (busy) return;
    const candidate = $('candidate').files[0];
    if (!candidate) { $('status').textContent = 'Choose your watch photo first.'; return; }
    busy = true;
    $('analyse').disabled = true;
    // Prevent inputs changing underneath a running result.
    const controls = [...document.querySelectorAll('input, select, button')].filter(el => el !== cancel);
    const disabled = controls.map(el => el.disabled);
    controls.forEach(el => { el.disabled = true; });
    const started = Date.now();
    try {
      $('status').textContent = 'Preparing comparison…';
      const form = new FormData();
      form.append('mode', state.task === 'gen' ? 'gen' : 'qc');
      form.append('model_ref', $('model').value);
      form.append('candidate', candidate);
      let reference = $('reference')?.files?.[0];
      if (!reference && state.task === 'gen' && $('refSelector').value) {
        const filename = decodeURIComponent($('refSelector').value);
        const response = await fetch('/api/v1/ux/reference-image/' + $('model').value + '/' + encodeURIComponent(filename),
                                     { signal: AbortSignal.timeout(15000) });
        if (!response.ok) throw new Error('Selected reference could not be loaded.');
        const blob = await response.blob();
        reference = new File([blob], filename, { type: blob.type || 'image/jpeg' });
      }
      if (reference) form.append('reference', reference);
      const accepted = await request('/api/v1/comparison-jobs', { method: 'POST', body: form });
      jobId = accepted.job_id;
      cancel.hidden = false;
      while (true) {
        const job = await request('/api/v1/comparison-jobs/' + jobId);
        $('status').textContent = job.message + ' (' + job.elapsed_seconds + 's)';
        if (job.state === 'complete') { show(job.result); $('status').textContent = 'Analysis complete.'; break; }
        if (job.state !== 'running') { $('status').textContent = job.message; break; }
        if (Date.now() - started > 210000) throw new Error('Could not retrieve the result. Please try again.');
        await new Promise(resolve => setTimeout(resolve, 700));
      }
    } catch (error) {
      if (jobId) {
        try { await request('/api/v1/comparison-jobs/' + jobId, { method: 'DELETE' }); } catch {}
      }
      $('status').textContent = error.name === 'TimeoutError' ? 'The app took too long to respond. Please try again.' : error.message;
    } finally {
      controls.forEach((el, index) => { el.disabled = disabled[index]; });
      $('analyse').disabled = false;
      cancel.hidden = true;
      jobId = null;
      busy = false;
    }
  };
})();
