const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const outDir = path.resolve("docs/kit-audio-docente");
const out = path.join(outDir, "kit-institucional-audio-docente-aiep-osorno.docx");
const mediaDir = path.resolve("docs/kit-audio-assets");

const BLUE = "003B71";
const RED = "E30613";
const LIGHT = "F3F6FA";
const LINE = "D6DADF";
const TEXT = "1F2937";
const MUTED = "5B6777";

const products = [
  {
    key: "dji",
    image: "thumb-dji-mic-mini.png",
    contentType: "image/png",
    name: "DJI Mic Mini 1TX+1RX",
    qty: 3,
    unit: 89900,
    total: 269700,
    provider: "ProMovil",
    url: "https://www.promovil.cl/products/dji-mic-mini-1tx-1rx",
    role: "Micrófonos de solapa/clip para captar voz docente con alta movilidad.",
    why: "Base del kit para clases, cápsulas, demos y subtitulado en tiempo real.",
  },
  {
    key: "boya",
    image: "thumb-boya-by-v10.png",
    contentType: "image/png",
    name: "BOYA BY-V10 USB-C",
    qty: 3,
    unit: 38990,
    total: 116970,
    provider: "Casa Royal",
    url: "https://www.casaroyal.cl/boya-by-v10-microfono-inalambrico-ultra-compacto-y-portable-2-4ghz-conector-usb-c/p",
    role: "Micrófonos USB-C directos al celular para usarlo como micrófono de la app.",
    why: "Permiten probar la experiencia real del docente: celular, QR y captura de voz.",
  },
  {
    key: "rode",
    image: "thumb-rode-wireless-me.png",
    contentType: "image/png",
    name: "RODE Wireless ME",
    qty: 1,
    unit: 175890,
    total: 175890,
    provider: "Gearhub",
    url: "https://gearhub.cl/collections/productos-nuevos-de-gearhub/products/rode-wireless-me-microfono-inalambrico-portatil",
    role: "Kit inalámbrico de estándar superior para demos, pitch y respaldo institucional.",
    why: "Entrega una referencia de calidad para socialización, grabación y pilotos clave.",
  },
  {
    key: "jabra",
    image: "thumb-jabra-speak2-55.png",
    contentType: "image/png",
    name: "Jabra Speak2 55",
    qty: 1,
    unit: 185336,
    total: 185336,
    provider: "Elite Center",
    url: "https://store.elitecenter.cl/shop/audio-y-video/parlantes/parlantes-bocinas-cornetas-perifericos/parlante-jabra-speak2-55-altavoz-universal-usb-tipo-c-negro/",
    role: "Speakerphone para captar voz grupal, preguntas de estudiantes y clases híbridas.",
    why: "Extiende el kit más allá del docente: reuniones, retroalimentación y aula híbrida.",
  },
  {
    key: "ath",
    image: "thumb-ath-m20x.png",
    contentType: "image/png",
    name: "Audio-Technica ATH-M20x",
    qty: 2,
    unit: 59900,
    total: 119800,
    provider: "La Casa del Músico",
    url: "https://lacasadelmusico.cl/producto/ath-m20x/",
    role: "Audífonos de monitoreo para revisar ruido, claridad y calidad de captura.",
    why: "Ayudan a validar si el audio es apto antes de usar subtitulado o generar material.",
  },
];

const accessories = {
  name: "Accesorios de audio, adaptadores, cables y estuche",
  qty: 1,
  unit: 132304,
  total: 132304,
  provider: "Compra institucional",
  url: "",
  role: "Compatibilidad, resguardo y operación del kit.",
  why: "Adaptadores USB-C/3,5 mm, cables de respaldo, extensiones cortas y estuche para préstamo interno.",
};

const allItems = [...products, accessories];

function money(n) {
  return `$${new Intl.NumberFormat("es-CL").format(n)}`;
}

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function run(r) {
  const attrs = [];
  if (r.bold) attrs.push("<w:b/>");
  if (r.italic) attrs.push("<w:i/>");
  if (r.size) attrs.push(`<w:sz w:val="${r.size}"/>`);
  if (r.color) attrs.push(`<w:color w:val="${r.color}"/>`);
  if (r.link) attrs.push("<w:u w:val=\"single\"/>");
  const rPr = attrs.length ? `<w:rPr>${attrs.join("")}</w:rPr>` : "";
  return `<w:r>${rPr}<w:t xml:space="preserve">${esc(r.text || "")}</w:t></w:r>`;
}

function p(text = "", opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text }];
  const pPr = [
    opts.after != null ? `<w:spacing w:after="${opts.after}"/>` : "",
    opts.before != null ? `<w:spacing w:before="${opts.before}" w:after="${opts.after || 0}"/>` : "",
    opts.align ? `<w:jc w:val="${opts.align}"/>` : "",
    opts.keepNext ? "<w:keepNext/>" : "",
    opts.border ? `<w:pBdr><w:bottom w:val="single" w:sz="6" w:space="2" w:color="${LINE}"/></w:pBdr>` : "",
  ].join("");
  return `<w:p>${pPr ? `<w:pPr>${pPr}</w:pPr>` : ""}${runs.map(run).join("")}</w:p>`;
}

function hyperlink(text, relId) {
  return `<w:p><w:pPr><w:spacing w:after="40"/></w:pPr><w:hyperlink r:id="${relId}" w:history="1"><w:r><w:rPr><w:color w:val="${BLUE}"/><w:u w:val="single"/><w:sz w:val="18"/></w:rPr><w:t>${esc(text)}</w:t></w:r></w:hyperlink></w:p>`;
}

function bullet(text) {
  return p([{ text: "• ", bold: true, color: RED }, { text }], { after: 45 });
}

function cell(content, width, opts = {}) {
  const shading = opts.shade ? `<w:shd w:fill="${opts.shade === true ? LIGHT : opts.shade}"/>` : "";
  const borders = opts.border === false ? "" : `<w:tcBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="${LINE}"/><w:left w:val="single" w:sz="4" w:space="0" w:color="${LINE}"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="${LINE}"/><w:right w:val="single" w:sz="4" w:space="0" w:color="${LINE}"/></w:tcBorders>`;
  const vAlign = opts.vAlign ? `<w:vAlign w:val="${opts.vAlign}"/>` : "";
  return `<w:tc><w:tcPr><w:tcW w:w="${width}" w:type="dxa"/>${shading}${borders}${vAlign}</w:tcPr>${content}</w:tc>`;
}

function row(cells) {
  return `<w:tr>${cells.join("")}</w:tr>`;
}

function table(rows, widths, opts = {}) {
  const grid = widths.map((w) => `<w:gridCol w:w="${w}"/>`).join("");
  const spacing = opts.before ? p("", { after: opts.before }) : "";
  return `${spacing}<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>${grid}</w:tblGrid>${rows.join("")}</w:tbl>`;
}

function image(relId, cx, cy) {
  return `<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="35"/></w:pPr><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" distT="0" distB="0" distL="0" distR="0"><wp:extent cx="${cx}" cy="${cy}"/><wp:docPr id="${Math.floor(Math.random() * 100000)}" name="producto"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="producto"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="${relId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>`;
}

function pageBreak() {
  return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>';
}

function headerBlock(title, subtitle) {
  return [
    p([{ text: title, bold: true, size: 31, color: BLUE }], { after: 25 }),
    p([{ text: subtitle, color: MUTED }], { after: 90, border: true }),
  ].join("");
}

function productCard(product, imgRel, linkRel) {
  const top = table([
    row([
      cell(image(imgRel, 1150000, 850000), 1450, { border: false, vAlign: "center" }),
      cell([
        p([{ text: product.name, bold: true, color: BLUE, size: 20 }], { after: 20 }),
        p([{ text: `${product.qty} x ${money(product.unit)} = `, color: MUTED, size: 17 }, { text: money(product.total), bold: true, color: TEXT, size: 17 }], { after: 20 }),
        hyperlink(product.provider, linkRel),
      ].join(""), 3050, { border: false, vAlign: "center" }),
    ]),
  ], [1450, 3050]);
  return [
    top,
    p([{ text: "Uso: ", bold: true }, { text: product.role }], { after: 35 }),
    p([{ text: "Aporte: ", bold: true }, { text: product.why }], { after: 0 }),
  ].join("");
}

const subtotal = allItems.reduce((sum, item) => sum + item.total, 0);
if (subtotal !== 1000000) throw new Error(`Budget total must be $1.000.000, got ${subtotal}`);

const imageRels = products.map((product, index) => ({ id: `rIdImg${index + 1}`, product }));
const linkRels = products.map((product, index) => ({ id: `rIdLink${index + 1}`, product }));

const budgetRows = [
  row([
    cell(p([{ text: "Ítem", bold: true, color: "FFFFFF" }], { after: 0 }), 6950, { shade: BLUE }),
    cell(p([{ text: "Cant.", bold: true, color: "FFFFFF" }], { after: 0, align: "center" }), 950, { shade: BLUE }),
    cell(p([{ text: "Monto", bold: true, color: "FFFFFF" }], { after: 0, align: "right" }), 1900, { shade: BLUE }),
  ]),
  ...allItems.map((item, i) => row([
    cell([
      p([{ text: item.name, bold: true }], { after: 18 }),
      p([{ text: item.role, color: MUTED, size: 18 }], { after: 0 }),
    ].join(""), 6950, { shade: i % 2 ? false : LIGHT }),
    cell(p(String(item.qty), { after: 0, align: "center" }), 950, { shade: i % 2 ? false : LIGHT }),
    cell(p([{ text: money(item.total), bold: true }], { after: 0, align: "right" }), 1900, { shade: i % 2 ? false : LIGHT }),
  ])),
  row([
    cell(p([{ text: "Total presupuesto proyecto", bold: true, color: "FFFFFF" }], { after: 0 }), 7900, { shade: BLUE }),
    cell(p([{ text: money(subtotal), bold: true, color: "FFFFFF" }], { after: 0, align: "right" }), 1900, { shade: BLUE }),
  ]),
];

const page1 = [
  headerBlock("Kit Institucional de Audio Docente AIEP Osorno", "Aula Subtitulada AIEP | Concurso Innovación Docente 2026"),
  table([row([cell([
    p([{ text: "Resumen ejecutivo", bold: true, color: BLUE, size: 22 }], { after: 40 }),
    p("El presupuesto propuesto conforma una capacidad instalada de audio docente para AIEP Osorno, reutilizable por distintas asignaturas, docentes y actividades académicas.", { after: 40 }),
    p("La mejora de audio es estratégica: mientras más clara sea la captura de voz, mejor será la precisión del subtitulado en tiempo real y mayor será el potencial para clases híbridas, cápsulas, demos, pitch y socialización de buenas prácticas.", { after: 0 }),
  ].join(""), 10300, { shade: LIGHT })])], [10300]),
  p([{ text: "Presupuesto detallado", bold: true, size: 24, color: BLUE }], { before: 120, after: 55 }),
  table(budgetRows, [6950, 950, 1900]),
  p([{ text: "Criterio de uso institucional", bold: true, size: 22, color: BLUE }], { before: 120, after: 45 }),
  table([row([
    cell([
      bullet("Kit orientado a audio: micrófonos, captura grupal, monitoreo y compatibilidad."),
      bullet("Equipamiento reutilizable por AIEP Osorno en clases, reuniones, demostraciones y producción docente."),
      bullet("Compra defendible ante bases: equipos concretos, trazables y vinculados al piloto de accesibilidad."),
    ].join(""), 10300, { shade: LIGHT }),
  ])], [10300]),
  p([{ text: "Proyección institucional", bold: true, size: 22, color: BLUE }], { before: 110, after: 45 }),
  table([row([
    cell(p([{ text: "Equipamiento institucional", bold: true, color: BLUE }, { text: ": kit de audio disponible para uso docente en sede." }], { after: 0 }), 5050, { shade: LIGHT }),
    cell(p([{ text: "Uso ampliable", bold: true, color: BLUE }, { text: ": apoyo para subtítulos, cápsulas, clases híbridas, reuniones y demos." }], { after: 0 }), 5050, { shade: LIGHT }),
  ])], [5050, 5050]),
];

const cards = [
  row([
    cell(productCard(products[0], "rIdImg1", "rIdLink1"), 5050, { shade: LIGHT }),
    cell(productCard(products[1], "rIdImg2", "rIdLink2"), 5050, { shade: LIGHT }),
  ]),
  row([
    cell(productCard(products[2], "rIdImg3", "rIdLink3"), 5050),
    cell(productCard(products[3], "rIdImg4", "rIdLink4"), 5050),
  ]),
  row([
    cell(productCard(products[4], "rIdImg5", "rIdLink5"), 5050, { shade: LIGHT }),
    cell([
      p([{ text: accessories.name, bold: true, color: BLUE, size: 20 }], { after: 30 }),
      p([{ text: `${money(accessories.total)} reservados`, bold: true }], { after: 45 }),
      p([{ text: "Uso: ", bold: true }, { text: accessories.role }], { after: 35 }),
      p([{ text: "Aporte: ", bold: true }, { text: accessories.why }], { after: 35 }),
      p("Debe rendirse con boleta/factura y ajustarse a compras institucionales.", { after: 0 }),
    ].join(""), 5050, { shade: LIGHT }),
  ]),
];

const page2 = [
  pageBreak(),
  headerBlock("Fichas de productos y trazabilidad", "Kit Institucional de Audio Docente AIEP Osorno"),
  table(cards, [5050, 5050]),
  p([{ text: "Observaciones de implementación", bold: true, color: BLUE, size: 22 }], { before: 100, after: 40 }),
  table([row([cell([
    bullet("Precios referenciales consultados en línea en mayo de 2026; antes de compra se debe validar stock, despacho y cotización institucional."),
    bullet("El equipamiento quedaría como capacidad instalada de AIEP Osorno y podría utilizarse en asignaturas, demos, capacitaciones o clases híbridas."),
    bullet("Si un proveedor no está disponible, se puede reemplazar por equipo equivalente manteniendo el foco: audio docente, captura móvil y reutilización institucional."),
  ].join(""), 10300, { shade: LIGHT })])], [10300]),
];

const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>
${page1.join("")}
${page2.join("")}
<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="650" w:right="680" w:bottom="650" w:left="680" w:header="650" w:footer="650" w:gutter="0"/></w:sectPr>
</w:body></w:document>`;

const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`;
const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`;
const docRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${imageRels.map(({ id, product }) => `<Relationship Id="${id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${product.image}"/>`).join("")}${linkRels.map(({ id, product }) => `<Relationship Id="${id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${esc(product.url)}" TargetMode="External"/>`).join("")}</Relationships>`;

function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let j = 0; j < 8; j++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[i] = c >>> 0;
    }
  }
  let c = 0xffffffff;
  for (const b of buf) c = table[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function dosDateTime(d = new Date()) {
  const time = ((d.getHours() & 0x1f) << 11) | ((d.getMinutes() & 0x3f) << 5) | (Math.floor(d.getSeconds() / 2) & 0x1f);
  const date = (((d.getFullYear() - 1980) & 0x7f) << 9) | (((d.getMonth() + 1) & 0xf) << 5) | (d.getDate() & 0x1f);
  return { time, date };
}

function makeZip(files) {
  const chunks = [];
  const central = [];
  let offset = 0;
  const dt = dosDateTime();
  for (const f of files) {
    const name = Buffer.from(f.name, "utf8");
    const data = Buffer.isBuffer(f.data) ? f.data : Buffer.from(f.data, "utf8");
    const deflated = zlib.deflateRawSync(data);
    const crc = crc32(data);
    const local = Buffer.alloc(30 + name.length);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(8, 8);
    local.writeUInt16LE(dt.time, 10);
    local.writeUInt16LE(dt.date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(deflated.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    name.copy(local, 30);
    chunks.push(local, deflated);

    const cent = Buffer.alloc(46 + name.length);
    cent.writeUInt32LE(0x02014b50, 0);
    cent.writeUInt16LE(20, 4);
    cent.writeUInt16LE(20, 6);
    cent.writeUInt16LE(0x0800, 8);
    cent.writeUInt16LE(8, 10);
    cent.writeUInt16LE(dt.time, 12);
    cent.writeUInt16LE(dt.date, 14);
    cent.writeUInt32LE(crc, 16);
    cent.writeUInt32LE(deflated.length, 20);
    cent.writeUInt32LE(data.length, 24);
    cent.writeUInt16LE(name.length, 28);
    cent.writeUInt16LE(0, 30);
    cent.writeUInt16LE(0, 32);
    cent.writeUInt16LE(0, 34);
    cent.writeUInt16LE(0, 36);
    cent.writeUInt32LE(0, 38);
    cent.writeUInt32LE(offset, 42);
    name.copy(cent, 46);
    central.push(cent);
    offset += local.length + deflated.length;
  }
  const centralSize = central.reduce((n, b) => n + b.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...chunks, ...central, end]);
}

fs.mkdirSync(outDir, { recursive: true });
const files = [
  { name: "[Content_Types].xml", data: contentTypes },
  { name: "_rels/.rels", data: rels },
  { name: "word/_rels/document.xml.rels", data: docRels },
  { name: "word/document.xml", data: documentXml },
  ...products.map((product) => ({
    name: `word/media/${product.image}`,
    data: fs.readFileSync(path.join(mediaDir, product.image)),
  })),
];
fs.writeFileSync(out, makeZip(files));
console.log(out);
