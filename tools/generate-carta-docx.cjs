const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const out = path.resolve("carta-word/carta-apoyo-directora-innovacion-docente-2026.docx");

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function p(text = "", opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text }];
  const pPr = [
    opts.after != null ? `<w:spacing w:after="${opts.after}"/>` : "",
    opts.align ? `<w:jc w:val="${opts.align}"/>` : "",
  ].join("");
  return `<w:p>${pPr ? `<w:pPr>${pPr}</w:pPr>` : ""}${runs.map((r) => {
    const rPr = [r.bold ? "<w:b/>" : "", r.size ? `<w:sz w:val="${r.size}"/>` : ""].join("");
    return `<w:r>${rPr ? `<w:rPr>${rPr}</w:rPr>` : ""}<w:t xml:space="preserve">${esc(r.text || "")}</w:t></w:r>`;
  }).join("")}</w:p>`;
}

function cell(content, width, opts = {}) {
  const borders = opts.bottom
    ? '<w:tcBorders><w:bottom w:val="single" w:sz="6" w:space="0" w:color="000000"/></w:tcBorders>'
    : "";
  return `<w:tc><w:tcPr><w:tcW w:w="${width}" w:type="dxa"/>${borders}</w:tcPr>${content}</w:tc>`;
}

function row(cells) {
  return `<w:tr>${cells.join("")}</w:tr>`;
}

function table(rows, widths) {
  const grid = widths.map((w) => `<w:gridCol w:w="${w}"/>`).join("");
  return `<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>${grid}</w:tblGrid>${rows.join("")}</w:tbl>`;
}

const title = p([{ text: "Carta de apoyo institucional", bold: true, size: 28 }], { after: 40 });
const subtitle = p([{ text: "Concurso Innovación Docente AIEP 2026", size: 20 }], { after: 120 });

const infoRows = [
  ["Sede", "Osorno"],
  ["Escuela", "Ingeniería, Energía y Tecnología"],
  ["Proyecto", "Aula Subtitulada AIEP: subtítulos flotantes en tiempo real para clases inclusivas"],
  ["Línea", "Innovación en el Aula"],
  ["Postulante", "Diego Matías Obando Aguilera"],
].map(([a, b]) => row([
  cell(p([{ text: a, bold: true }], { after: 0 }), 2100),
  cell(p(b, { after: 0 }), 7200),
]));

const dateLine = p("Osorno, __________ de ____________________ de 2026", { after: 160 });

const body = [
  p("Señores/as", { after: 0 }),
  p([{ text: "Comité Concurso Innovación Docente 2026", bold: true }], { after: 0 }),
  p("AIEP", { after: 0 }),
  p("Presente", { after: 120 }),
  p("De mi consideración:", { after: 120 }),
  p([
    { text: "Por medio de la presente, manifiesto mi apoyo institucional a la postulación del proyecto " },
    { text: "“Aula Subtitulada AIEP: subtítulos flotantes en tiempo real para clases inclusivas”", bold: true },
    { text: ", liderado por el docente " },
    { text: "Diego Matías Obando Aguilera", bold: true },
    { text: ", en el marco del " },
    { text: "Concurso Innovación Docente AIEP 2026", bold: true },
    { text: ", línea " },
    { text: "Innovación en el Aula", bold: true },
    { text: "." },
  ], { after: 100 }),
  p("La iniciativa propone implementar y validar en aula una solución de accesibilidad que permite transformar la voz del docente en subtítulos visibles en tiempo real, mediante una aplicación de apoyo pedagógico pensada para reducir barreras de comprensión, participación y acceso a instrucciones durante la clase. El proyecto se alinea con el Diseño Universal para el Aprendizaje, al ofrecer una vía visual complementaria para todo el curso.", { after: 100 }),
  p([
    { text: "El origen de esta propuesta surge a partir de una oportunidad identificada por " },
    { text: "Elías Alberto Silva Carrasco", bold: true },
    { text: ", Jefe de Escuela de Ingeniería, Energía y Tecnología de sede Osorno, quien planteó avanzar hacia una herramienta que apoyara a estudiantes con barreras auditivas mediante subtitulado o traducción de lo hablado por docentes. A partir de esa orientación, Diego Matías Obando Aguilera lideró el desarrollo del prototipo funcional y propone su implementación pedagógica, evaluación y mejora durante el segundo semestre de 2026." },
  ], { after: 100 }),
  p("Como Dirección, valoramos esta postulación por su pertinencia institucional, su foco inclusivo, su potencial de escalamiento a otras asignaturas y sedes, y su contribución a metodologías de enseñanza-aprendizaje apoyadas por tecnología. Asimismo, comprometemos disposición para facilitar la coordinación académica necesaria, definir la sección de aplicación, resguardar la implementación y apoyar la socialización de resultados en la sede.", { after: 100 }),
  p("Sin otro particular, y esperando una favorable acogida, saluda atentamente,", { after: 220 }),
];

const sig = table([
  row([cell(p([{ text: "Firma:", bold: true }], { after: 0 }), 1400), cell(p("", { after: 0 }), 5300, { bottom: true })]),
  row([cell(p([{ text: "Nombre:", bold: true }], { after: 0 }), 1400), cell(p("", { after: 0 }), 5300, { bottom: true })]),
  row([cell(p([{ text: "Cargo:", bold: true }], { after: 0 }), 1400), cell(p("", { after: 0 }), 5300, { bottom: true })]),
], [1400, 5300]);

const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>
${title}${subtitle}<w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="1" w:color="000000"/></w:pBdr><w:spacing w:after="120"/></w:pPr></w:p>
${table(infoRows, [2100, 7200])}
${p("", { after: 80 })}${dateLine}
${body.join("")}
${sig}
<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="720" w:right="900" w:bottom="720" w:left="900" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>
</w:body></w:document>`;

const contentTypes = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>';
const rels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>';
const docRels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>';

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
    const data = Buffer.from(f.data, "utf8");
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
fs.writeFileSync(out, makeZip([
  { name: "[Content_Types].xml", data: contentTypes },
  { name: "_rels/.rels", data: rels },
  { name: "word/_rels/document.xml.rels", data: docRels },
  { name: "word/document.xml", data: documentXml },
]));

console.log(out);
