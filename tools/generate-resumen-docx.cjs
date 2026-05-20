const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const out = path.resolve("resumen-word/resumen-directora-aula-subtitulada-2026.docx");

const images = [
  ["rIdImg1", "desktop-overlay-esperando.png", "postulacion-assets/desktop-overlay-esperando.png", "image/png"],
  ["rIdImg2", "desktop-overlay-transcribiendo.png", "postulacion-assets/desktop-overlay-transcribiendo.png", "image/png"],
  ["rIdImg3", "movil-captura-detenida.jpeg", "postulacion-assets/movil-captura-detenida.jpeg", "image/jpeg"],
  ["rIdImg4", "movil-captura-activa.jpeg", "postulacion-assets/movil-captura-activa.jpeg", "image/jpeg"],
];

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function p(text = "", opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text }];
  const pPr = [
    opts.after != null ? `<w:spacing w:after="${opts.after}"/>` : "",
    opts.before != null ? `<w:spacing w:before="${opts.before}" w:after="${opts.after || 0}"/>` : "",
    opts.align ? `<w:jc w:val="${opts.align}"/>` : "",
    opts.border ? '<w:pBdr><w:bottom w:val="single" w:sz="4" w:space="1" w:color="D6DADF"/></w:pBdr>' : "",
  ].join("");
  return `<w:p>${pPr ? `<w:pPr>${pPr}</w:pPr>` : ""}${runs.map((r) => {
    const rPr = [
      r.bold ? "<w:b/>" : "",
      r.size ? `<w:sz w:val="${r.size}"/>` : "",
      r.color ? `<w:color w:val="${r.color}"/>` : "",
    ].join("");
    return `<w:r>${rPr ? `<w:rPr>${rPr}</w:rPr>` : ""}<w:t xml:space="preserve">${esc(r.text || "")}</w:t></w:r>`;
  }).join("")}</w:p>`;
}

function bullet(text) {
  return p(`• ${text}`, { after: 60 });
}

function cell(content, width, opts = {}) {
  const shading = opts.shade ? '<w:shd w:fill="F3F4F6"/>' : "";
  const borders = opts.bottom ? '<w:tcBorders><w:bottom w:val="single" w:sz="4" w:space="0" w:color="D6DADF"/></w:tcBorders>' : "";
  return `<w:tc><w:tcPr><w:tcW w:w="${width}" w:type="dxa"/>${shading}${borders}</w:tcPr>${content}</w:tc>`;
}

function row(cells) {
  return `<w:tr>${cells.join("")}</w:tr>`;
}

function table(rows, widths, opts = {}) {
  const grid = widths.map((w) => `<w:gridCol w:w="${w}"/>`).join("");
  const layout = opts.autofit ? "" : '<w:tblLayout w:type="fixed"/>';
  return `<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/>${layout}</w:tblPr><w:tblGrid>${grid}</w:tblGrid>${rows.join("")}</w:tbl>`;
}

function image(rId, cx, cy) {
  return `<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="60"/></w:pPr><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" distT="0" distB="0" distL="0" distR="0"><wp:extent cx="${cx}" cy="${cy}"/><wp:docPr id="${Math.floor(Math.random() * 100000)}" name="Imagen"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="captura"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="${rId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>`;
}

function pageBreak() {
  return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>';
}

const metaRows = [
  ["Foco principal", "Accesibilidad e inclusión en aula, con tecnología como medio de apoyo."],
  ["Postulante y líder", "Diego Matías Obando Aguilera."],
  ["Impulsor institucional", "Elías Alberto Silva Carrasco, Jefe de Escuela de Ingeniería, Energía y Tecnología, sede Osorno."],
  ["Línea propuesta", "Innovación en el Aula."],
].map(([a, b]) => row([cell(p([{ text: a, bold: true }], { after: 0 }), 2300), cell(p(b, { after: 0 }), 7000)]));

const flowRows = [
  ["1", "El docente abre la aplicación en el computador."],
  ["2", "Escanea el QR con su celular y activa el micrófono."],
  ["3", "La voz se transforma en subtítulos visibles en tiempo real."],
  ["4", "Con autorización, la transcripción puede servir para generar resúmenes, glosarios, fichas o infografías."],
].map(([a, b]) => row([cell(p([{ text: a, bold: true }], { after: 0 }), 500), cell(p(b, { after: 0 }), 8800)]));

const nextRows = [
  ["Pendiente principal", "Definir sección o docente complementario para cumplir el mínimo de 15 estudiantes participantes."],
  ["Apoyo solicitado", "Carta de apoyo institucional para adjuntar en la postulación."],
  ["Resultado esperado", "Piloto medido, ajustes del prototipo y material de replicabilidad para la sede."],
].map(([a, b]) => row([cell(p([{ text: a, bold: true }], { after: 0 }), 2500), cell(p(b, { after: 0 }), 6800)]));

const page1 = [
  p([{ text: "Aula Subtitulada AIEP", bold: true, size: 32 }], { after: 20 }),
  p("Resumen breve para Dirección | Concurso Innovación Docente 2026", { after: 140, border: true }),
  p([{ text: "Resumen ejecutivo", bold: true, size: 24 }], { after: 80 }),
  table([row([cell(p([
    { text: "Aula Subtitulada AIEP", bold: true },
    { text: " es una aplicación de apoyo pedagógico que muestra subtítulos flotantes en tiempo real durante la clase. El docente usa su celular como micrófono; el computador recibe la transcripción y presenta el texto sobre la presentación, navegador, aula virtual o herramienta que esté usando." },
  ], { after: 0 }), 9300, { shade: true })])], [9300]),
  p("", { after: 80 }),
  table(metaRows, [2300, 7000]),
  p([{ text: "Interfaz docente", bold: true, size: 24 }], { before: 160, after: 80 }),
  table([
    row([
      cell(image("rIdImg1", 3900000, 2250000) + p([{ text: "Vista general en PC. ", bold: true }, { text: "La app muestra el QR, configuración y subtítulo flotante." }], { after: 0 }), 4550),
      cell(image("rIdImg2", 3900000, 2250000) + p([{ text: "Subtitulado activo. ", bold: true }, { text: "Lo hablado se presenta como texto visible y movible." }], { after: 0 }), 4550),
    ]),
  ], [4550, 4550]),
  p([{ text: "Uso en clase", bold: true, size: 24 }], { before: 160, after: 60 }),
  table(flowRows, [500, 8800]),
  p([{ text: "Aporte esperado", bold: true }], { before: 120, after: 40 }),
  bullet("Reduce barreras de acceso al discurso oral: audición, atención, lenguaje, ubicación en sala o vocabulario técnico."),
  bullet("Se basa en Diseño Universal para el Aprendizaje: el apoyo queda disponible para todo el curso."),
  bullet("Es replicable con recursos simples: computador docente, celular y guía de implementación."),
];

const page2 = [
  pageBreak(),
  p([{ text: "Vista móvil y proyección pedagógica", bold: true, size: 32 }], { after: 20 }),
  p("Aula Subtitulada AIEP | Resumen para Dirección", { after: 140, border: true }),
  p([{ text: "Micrófono desde celular", bold: true, size: 24 }], { after: 80 }),
  table([
    row([
      cell(image("rIdImg3", 2400000, 5200000) + p([{ text: "Antes de iniciar. ", bold: true }, { text: "El celular queda conectado al PC docente y preparado para capturar audio." }], { after: 0 }), 4550),
      cell(image("rIdImg4", 2400000, 5200000) + p([{ text: "Captura activa. ", bold: true }, { text: "El texto reconocido se refleja en el PC como subtítulo de apoyo." }], { after: 0 }), 4550),
    ]),
  ], [4550, 4550]),
  p([{ text: "Estado actual", bold: true, size: 24 }], { before: 160, after: 60 }),
  p("El prototipo funcional ya conecta celular y computador, muestra subtítulos flotantes, permite mover el panel en pantalla y registra transcripciones cuando el docente lo autoriza. La postulación busca validarlo pedagógicamente durante el segundo semestre 2026, levantar evidencia de utilidad con estudiantes y preparar una guía para transferir la experiencia a otros contextos. Como proyección, las transcripciones autorizadas podrán transformarse en materiales de apoyo: resúmenes, glosarios, fichas de repaso o infografías breves.", { after: 100 }),
  p([{ text: "Siguiente paso para la postulación", bold: true, size: 24 }], { after: 60 }),
  table(nextRows, [2500, 6800]),
];

const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>
${page1.join("")}
${page2.join("")}
<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="720" w:right="780" w:bottom="720" w:left="780" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>
</w:body></w:document>`;

const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`;
const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`;
const docRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${images.map(([id, name]) => `<Relationship Id="${id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${name}"/>`).join("")}</Relationships>`;

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

fs.mkdirSync(path.dirname(out), { recursive: true });
const files = [
  { name: "[Content_Types].xml", data: contentTypes },
  { name: "_rels/.rels", data: rels },
  { name: "word/_rels/document.xml.rels", data: docRels },
  { name: "word/document.xml", data: documentXml },
  ...images.map(([, name, file]) => ({ name: `word/media/${name}`, data: fs.readFileSync(file) })),
];
fs.writeFileSync(out, makeZip(files));
console.log(out);
