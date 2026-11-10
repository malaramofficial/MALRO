import { mkdir, copyFile } from "node:fs/promises";
import { resolve } from "node:path";
const root = process.cwd();
const out = resolve(root, "dist");
await mkdir(out, { recursive: true });
for (const file of ["index.html", "style.css", "app.js", "config.js"]) {
  await copyFile(resolve(root, file), resolve(out, file));
}
console.log("MALRO Admin static site built.");
