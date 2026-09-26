const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [,, input, output, size] = process.argv;
  const svg = fs.readFileSync(input, 'utf8');
  const s = parseInt(size || '512');
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || undefined });
  const page = await browser.newPage({ viewport: { width: s, height: s } });
  await page.setContent(`<html><body style="margin:0;background:#fff">${svg.replace('<svg', `<svg width="${s}" height="${s}"`)}</body></html>`);
  await page.screenshot({ path: output });
  await browser.close();
})();
