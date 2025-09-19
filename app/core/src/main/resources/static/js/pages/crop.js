let pdfCanvas = document.getElementById('cropPdfCanvas');
let overlayCanvas = document.getElementById('overlayCanvas');
let canvasesContainer = document.getElementById('canvasesContainer');
canvasesContainer.style.display = 'none';

let context = pdfCanvas.getContext('2d');
let overlayContext = overlayCanvas.getContext('2d');

overlayCanvas.width = pdfCanvas.width;
overlayCanvas.height = pdfCanvas.height;

let isDrawing = false; // New flag to check if drawing is ongoing

let cropForm = document.getElementById('cropForm');
let fileInput = document.getElementById('fileInput-input');
let xInput = document.getElementById('x');
let yInput = document.getElementById('y');
let widthInput = document.getElementById('width');
let heightInput = document.getElementById('height');

let pdfDoc = null;
let currentPage = 1;
let totalPages = 0;

let startX = 0;
let startY = 0;
let rectWidth = 0;
let rectHeight = 0;

let pageScale = 1; // The scale which the pdf page renders
let timeId = null; // timeout id for resizing canvases event

function renderPageFromFile(file) {
  if (file.type === 'application/pdf') {
    let reader = new FileReader();
    reader.onload = function (ev) {
      let typedArray = new Uint8Array(reader.result);
      pdfjsLib.GlobalWorkerOptions.workerSrc = './pdfjs-legacy/pdf.worker.mjs';
      pdfjsLib.getDocument(typedArray).promise.then(function (pdf) {
        pdfDoc = pdf;
        totalPages = pdf.numPages;
        renderPage(currentPage);
      });
    };
    reader.readAsArrayBuffer(file);
  }
}

window.addEventListener('resize', function () {
  clearTimeout(timeId);

  timeId = setTimeout(function () {
    if (fileInput.files.length == 0) return;
    let canvasesContainer = document.getElementById('canvasesContainer');
    let containerRect = canvasesContainer.getBoundingClientRect();

    context.clearRect(0, 0, pdfCanvas.width, pdfCanvas.height);

    overlayContext.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);

    pdfCanvas.width = containerRect.width;
    pdfCanvas.height = containerRect.height;

    overlayCanvas.width = containerRect.width;
    overlayCanvas.height = containerRect.height;

    let file = fileInput.files[0];
    renderPageFromFile(file);
  }, 1000);
});

fileInput.addEventListener('change', function (e) {
  fileInput.addEventListener('file-input-change', async (e) => {
    const {allFiles} = e.detail;
    if (allFiles && allFiles.length > 0) {
      canvasesContainer.style.display = 'block'; // set for visual purposes
      let file = allFiles[0];
      renderPageFromFile(file);
    }
  });
});

cropForm.addEventListener('submit', function (e) {
  if ((xInput.value === '' || yInput.value === '' || widthInput.value === '' || heightInput.value === '') ||
      (Number(widthInput.value) === 0 || Number(heightInput.value) === 0)) {
    xInput.value = 0;
    yInput.value = 0;
    widthInput.value = pdfCanvas.width / pageScale;
    heightInput.value = pdfCanvas.height / pageScale;
  }
});

overlayCanvas.addEventListener('mousedown', function (e) {
  // Clear previously drawn rectangle on the main canvas
  context.clearRect(0, 0, pdfCanvas.width, pdfCanvas.height);
  renderPage(currentPage); // Re-render the PDF

  // Clear the overlay canvas to ensure old drawings are removed
  overlayContext.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);

  startX = e.offsetX;
  startY = e.offsetY;
  isDrawing = true;
});

overlayCanvas.addEventListener('mousemove', function (e) {
  if (!isDrawing) return;
  overlayContext.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);

  const currentX = e.offsetX;
  const currentY = e.offsetY;
  const minX = Math.min(startX, currentX);
  const minY = Math.min(startY, currentY);
  const width = Math.abs(currentX - startX);
  const height = Math.abs(currentY - startY);

  overlayContext.strokeStyle = 'red';
  overlayContext.strokeRect(minX, minY, width, height);
});

overlayCanvas.addEventListener('mouseup', function (e) {
  isDrawing = false;

  const endX = e.offsetX;
  const endY = e.offsetY;

  const minX = Math.min(startX, endX);
  const maxX = Math.max(startX, endX);
  const minY = Math.min(startY, endY);
  const maxY = Math.max(startY, endY);

  rectWidth = maxX - minX;
  rectHeight = maxY - minY;

  const canvasHeight = pdfCanvas.height;

  xInput.value = minX / pageScale;
  yInput.value = (canvasHeight - maxY) / pageScale;
  widthInput.value = rectWidth / pageScale;
  heightInput.value = rectHeight / pageScale;

  // Draw the final rectangle on the main canvas
  context.strokeStyle = 'red';
  context.strokeRect(minX, minY, rectWidth, rectHeight);

  overlayContext.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height); // Clear the overlay
});

function renderPage(pageNumber) {
  pdfDoc.getPage(pageNumber).then(function (page) {
    let canvasesContainer = document.getElementById('canvasesContainer');
    let containerRect = canvasesContainer.getBoundingClientRect();

    pageScale = containerRect.width / page.getViewport({scale: 1}).width; // The new scale

    let viewport = page.getViewport({scale: containerRect.width / page.getViewport({scale: 1}).width});

    canvasesContainer.width = viewport.width;
    canvasesContainer.height = viewport.height;

    pdfCanvas.width = viewport.width;
    pdfCanvas.height = viewport.height;

    overlayCanvas.width = viewport.width; // Match overlay canvas size with PDF canvas
    overlayCanvas.height = viewport.height;

    let renderContext = {canvasContext: context, viewport: viewport};
    page.render(renderContext);
    pdfCanvas.classList.add('shadow-canvas');
  });
}
