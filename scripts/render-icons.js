// Renders the h.off app icons (into assets/) and the Google Play listing art
// (into assets/store/) from SVG. Needs Playwright with Chromium installed:
//   node scripts/render-icons.js
const { chromium } = require('playwright');
const path = require('path');

const assetsFolder = path.join(__dirname, '..', 'assets');
const storeFolder = path.join(assetsFolder, 'store');

const accent = '#1663D6';
const deep = '#0B3F91';

/** Washing-machine door with a power symbol: the "off" in h.off. */
function doorSvg({ size, scale = 1, background = true, colour = '#FFFFFF' }) {
  const c = size / 2;
  const s = (n) => n * scale * size / 1024;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    ${background ? `<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${accent}"/><stop offset="1" stop-color="${deep}"/></linearGradient></defs>
    <rect width="${size}" height="${size}" fill="url(#g)"/>` : ''}
    <circle cx="${c}" cy="${c}" r="${s(330)}" fill="none" stroke="${colour}" stroke-width="${s(56)}"/>
    <circle cx="${c}" cy="${c}" r="${s(250)}" fill="${colour}" fill-opacity="0.16"/>
    <path d="M ${c - s(110)} ${c - s(95)} A ${s(150)} ${s(150)} 0 1 0 ${c + s(110)} ${c - s(95)}"
      fill="none" stroke="${colour}" stroke-width="${s(48)}" stroke-linecap="round"/>
    <line x1="${c}" y1="${c - s(175)}" x2="${c}" y2="${c - s(10)}" stroke="${colour}" stroke-width="${s(48)}" stroke-linecap="round"/>
  </svg>`;
}

/** The adaptive icon's back layer: just the blue gradient. */
function backgroundSvg(size) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">
    <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${accent}"/><stop offset="1" stop-color="${deep}"/></linearGradient></defs>
    <rect width="${size}" height="${size}" fill="url(#g)"/></svg>`;
}

const outputs = [
  ['android-icon-background.png', 512, backgroundSvg(512)],
  ['favicon.png', 48, doorSvg({ size: 48 })],
  ['icon.png', 1024, doorSvg({ size: 1024 })],
  [path.join('store', 'play-icon-512.png'), 512, doorSvg({ size: 512 })],
  // Adaptive icon layers: Android crops to the middle ~66%, so the door is smaller.
  ['android-icon-foreground.png', 512, doorSvg({ size: 512, scale: 0.66, background: false })],
  ['android-icon-monochrome.png', 512, doorSvg({ size: 512, scale: 0.66, background: false, colour: '#000000' })],
  ['splash-icon.png', 1024, doorSvg({ size: 1024, scale: 0.8, background: false, colour: accent })],
];

const featureGraphic = `<html><body style="margin:0">
  <div style="width:1024px;height:500px;display:flex;align-items:center;gap:56px;padding:0 72px;box-sizing:border-box;
    background:linear-gradient(135deg, ${accent}, ${deep});font-family:system-ui,sans-serif;color:white">
    ${doorSvg({ size: 300, background: false })}
    <div>
      <div style="font-size:112px;font-weight:800;letter-spacing:-2px">h.off</div>
      <div style="font-size:34px;opacity:.9;margin-top:8px">A fast, simple remote<br>for your washing machine</div>
    </div>
  </div></body></html>`;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [name, size, svg] of outputs) {
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
    await page.screenshot({ path: path.join(assetsFolder, name), omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
  }
  await page.setViewportSize({ width: 1024, height: 500 });
  await page.setContent(featureGraphic);
  await page.screenshot({ path: path.join(storeFolder, 'feature-graphic.png') });
  await browser.close();
})();
