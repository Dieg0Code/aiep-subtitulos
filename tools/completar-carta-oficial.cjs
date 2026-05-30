const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const source = path.resolve("docs/oficiales-concurso-innovacion-docente-2026/CARTA DE APOYO.docx");
const variants = [
  {
    target: path.resolve("docs/oficiales-concurso-innovacion-docente-2026/CARTA DE APOYO - Aula Subtitulada AIEP.docx"),
    role: "Directora Académica",
  },
  {
    target: path.resolve("docs/oficiales-concurso-innovacion-docente-2026/CARTA DE APOYO - Aula Subtitulada AIEP - Director Academico.docx"),
    role: "Director Académico",
  },
];

function replacementsFor(role) {
  return new Map([
    ["Ciudad, día mes del año", "Osorno, ____ de __________ de 2026"],
    ["“(director/a académico", `“${role}`],
    [`“${role})”`, `“${role}”`],
    ["<w:t>)” de AIEP", "<w:t>” de AIEP"],
    [" o sub/director/a académico", ""],
    ["(Nombre Sede)", "Sede Osorno"],
    ["proyecto “(Nombre proyecto)”", "proyecto “Aula Subtitulada AIEP: subtítulos flotantes en tiempo real para clases inclusivas”"],
    ["representante (nombre docente)", "representante Diego Matías Obando Aguilera"],
  ]);
}

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

  for (const file of files) {
    const name = Buffer.from(file.name, "utf8");
    const data = file.data;
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

  const centralSize = central.reduce((sum, item) => sum + item.length, 0);
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

function parseZip(buffer) {
  const files = [];
  let offset = 0;
  while (offset < buffer.length && buffer.readUInt32LE(offset) === 0x04034b50) {
    const flags = buffer.readUInt16LE(offset + 6);
    const method = buffer.readUInt16LE(offset + 8);
    const compressedSize = buffer.readUInt32LE(offset + 18);
    const fileNameLength = buffer.readUInt16LE(offset + 26);
    const extraLength = buffer.readUInt16LE(offset + 28);
    const name = buffer.subarray(offset + 30, offset + 30 + fileNameLength).toString("utf8");
    const dataStart = offset + 30 + fileNameLength + extraLength;
    const dataEnd = dataStart + compressedSize;
    if ((flags & 0x08) !== 0) throw new Error(`Unsupported ZIP data descriptor in ${name}`);
    if (method !== 8 && method !== 0) throw new Error(`Unsupported ZIP method ${method} in ${name}`);
    const compressed = buffer.subarray(dataStart, dataEnd);
    const data = method === 8 ? zlib.inflateRawSync(compressed) : compressed;
    files.push({ name, data });
    offset = dataEnd;
  }
  return files;
}

for (const variant of variants) {
  const files = parseZip(fs.readFileSync(source));
  const document = files.find((file) => file.name === "word/document.xml");
  if (!document) throw new Error("word/document.xml not found");

  let xml = document.data.toString("utf8");
  for (const [from, to] of replacementsFor(variant.role).entries()) {
    xml = xml.replaceAll(from, to);
  }
  document.data = Buffer.from(xml, "utf8");

  fs.writeFileSync(variant.target, makeZip(files));
  console.log(variant.target);
}
