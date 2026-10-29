import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
const root = path.resolve('dist');
const type = (p) => p.endsWith('.html') ? 'text/html' : p.endsWith('.js') ? 'application/javascript' : p.endsWith('.css') ? 'text/css' : p.endsWith('.json') ? 'application/json' : p.endsWith('.webp') ? 'image/webp' : p.endsWith('.mp3') ? 'audio/mpeg' : p.endsWith('.woff2') ? 'font/woff2' : 'application/octet-stream';
const server = createServer(async (req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
  if (!u.startsWith('/assets/') || u.includes('..')) { res.writeHead(404); res.end(); return; }
  const p = path.join(root, u.slice('/assets/'.length) || 'index.html');
  try { const s = await stat(p); if (!s.isFile()) throw Error('not file'); res.writeHead(200, {'content-type': type(p), 'content-length': s.size}); res.end(await readFile(p)); }
  catch { res.writeHead(404); res.end(); }
});
await new Promise(resolve => server.listen(0,'127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({headless: true, executablePath:'/usr/bin/chromium-browser', args:['--no-sandbox']});
try {
  const page = await browser.newPage({ serviceWorkers:'block' });
  const failures = [];
  page.on('requestfailed', r => failures.push(`${r.url()} ${r.failure()?.errorText}`));
  page.on('pageerror', e => failures.push(`JS: ${e.message}`));
  await page.route('**/*', route => route.request().url().startsWith(origin) ? route.continue() : route.abort('internetdisconnected'));
  await page.goto(origin + '/assets/index.html', {waitUntil:'domcontentloaded'});
  await page.waitForSelector('.app', {timeout:30000});
  const count = await page.locator('.app').count();
  const atlas = await (await page.request.get(origin + '/assets/atlas/index.json')).json();
  const word = atlas.words[0][0];
  const urls = [`/assets/atlas/entries/0.json`, `/assets/atlas/img/${word}.webp`, `/assets/atlas/audio/uk/${word}.mp3`];
  for (const u of urls) { const r = await page.request.get(origin + u); if (!r.ok()) throw Error(`${u}: ${r.status()}`); }
  // Complete first-run overlays before exercising the real search interaction.
  await page.getByRole('button', { name: '跳过' }).click();
  await page.getByRole('button', { name: /出发/ }).click();
  // Search must open a full entry, not merely return index suggestions.
  await page.getByRole('button', { name: '搜索单词' }).click();
  const query = atlas.words[0][1];
  await page.getByRole('dialog', { name: '搜索单词' }).getByRole('textbox').fill(query);
  const match = page.locator('.palette-list [role="option"]').first();
  await match.waitFor();
  const selected = await match.locator('.palette-word').innerText();
  await match.click();
  await page.locator('.sheet[role="dialog"] .entry').waitFor({ timeout: 30000 });
  const entryOpened = await page.locator('.sheet[role="dialog"]').innerText();
  if (!entryOpened.toLowerCase().includes(selected.toLowerCase())) throw Error(`Search did not open entry for ${selected}`);
  await page.getByRole('button', { name: '关闭' }).click();
  await page.getByRole('link', { name: '图鉴' }).first().click();
  await page.getByRole('button', { name: '星图视图' }).click();
  await page.locator('.atlas-head input').fill('typing');
  const galaxyResult = page.getByLabel('星图搜索结果').getByRole('button', { name: /typing/ });
  await galaxyResult.waitFor();
  await galaxyResult.click();
  await page.locator('.sheet[role="dialog"] .entry').waitFor({ timeout: 30000 });
  if (!(await page.locator('.sheet[role="dialog"]').innerText()).toLowerCase().includes('typing')) throw Error('Galaxy search did not open typing entry');
  await page.getByRole('button', { name: '关闭' }).click();
  await page.getByRole('link', { name: '我的' }).first().click();
  await page.evaluate(() => { window.WordAtlasAndroid = { saveBackup: json => { window.__backupForSmoke = json; } }; });
  await page.getByRole('button', { name: '导出备份' }).click();
  const backup = await page.evaluate(() => window.__backupForSmoke);
  if (JSON.parse(backup).app !== 'word-atlas') throw Error('Export did not send a backup to Android bridge');
  await page.locator('input[type="file"][accept*="json"]').setInputFiles({ name: 'backup.json', mimeType: 'application/json', buffer: Buffer.from(backup) });
  await page.getByRole('status').getByText(/已恢复/).waitFor();
  console.log(JSON.stringify({ renderedApp: count===1, indexedWords: atlas.words.length, sampledWord:word, localMedia:urls, searchQuery:query, selected, entryOpened:true, galaxySearchOpened:'typing', backupExported:true, backupImported:true, blockedExternal:true, failures }, null,2));
  if (failures.length) process.exitCode=1;
} finally { await browser.close(); server.close(); }
