const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const source = path.resolve("docs/oficiales-concurso-innovacion-docente-2026/Formulario Offline Concurso Innovación docente 2026.docx");
const target = path.resolve("docs/oficiales-concurso-innovacion-docente-2026/Formulario Offline Concurso Innovación docente 2026 - Aula Subtitulada AIEP.docx");

const values = new Map([
  ["Nombre del proyecto:", "Aula Subtitulada AIEP: subtítulos flotantes en tiempo real para clases inclusivas"],
  ["Nombre responsable:", "Diego Matías Obando Aguilera"],
  ["Rut responsable:", "19.337.877-3"],
  ["Mail institucional:", "diego.obandoag@correoaiep.cl"],
  ["Mail personal:", "diegoobando20@gmail.com"],
  ["Teléfono:", "+56 9 6514 9967"],
  ["Sede:", "Osorno"],
  ["Escuela:", "Ingeniería, Energía y Tecnología"],
  [
    "Asignatura en el que desarrollará el proyecto:",
    "TPE401 Taller de proyecto de especialidad",
  ],
  ["Cantidad de estudiantes involucrados en el proyecto:", "15 estudiantes"],
  ["Nombre de integrante 1:", "Paulina María Beltrán Cerda"],
  ["Rut integrante 1:", "16.577.387-K"],
  ["Mail institucional integrante 1:", "paulina.beltran@correoaiep.cl"],
  ["Nombre de integrante 2:", "N/A"],
  ["Rut integrante 2:", "N/A"],
  ["Mail institucional integrante 2:", "N/A"],
  ["Monto total gastos operativos:", "0"],
  ["Gastos de inversión:", "1.000.000"],
  ["Gastos en recursos humanos:", "0"],
  ["Total:", "1.000.000"],
  [
    "Detalle gastos operativos:",
    "No se consideran gastos operativos en esta versión del presupuesto. La implementación pedagógica, evaluación breve, socialización interna y preparación de instrumentos se desarrollarán con recursos docentes e institucionales disponibles. El presupuesto se concentra en equipamiento de inversión para la sede, con foco en audio docente reutilizable y apoyo directo al piloto de subtitulado en tiempo real.",
  ],
  [
    "Detalle gastos de inversión:",
    "Kit institucional de audio docente para AIEP Osorno: 3 DJI Mic Mini 1TX+1RX, 3 BOYA BY-V10 USB-C para celular, 1 RODE Wireless ME, 1 Jabra Speak2 55, 2 audífonos Audio-Technica ATH-M20x y accesorios de audio, adaptadores, cables y estuche. La calidad de audio incide directamente en la precisión del subtitulado en tiempo real. El equipamiento quedará como capacidad instalada y podrá reutilizarse en clases, cápsulas, reuniones, demostraciones y actividades docentes.",
  ],
  [
    "Detalle gastos en recursos humanos:",
    "No se consideran gastos en recursos humanos. La implementación del piloto, ajustes pedagógicos, seguimiento y sistematización se realizarán por el equipo docente responsable, con apoyo institucional de la sede. La guía de replicabilidad se mantiene como producto pedagógico del proyecto, sin asociarse a contratación externa dentro del presupuesto solicitado.",
  ],
]);

const answers = new Map([
  [
    "Marco Teórico:",
    "La propuesta se sustenta en el Diseño Universal para el Aprendizaje, que promueve ofrecer múltiples formas de representación para reducir barreras de acceso al contenido. CAST plantea que diversificar la representación favorece percepción, lenguaje y comprensión. UNESCO enfatiza que la educación inclusiva debe identificar y remover barreras para que todo estudiante participe. En este marco, el subtitulado en tiempo real convierte la explicación oral del docente en apoyo visual inmediato, útil para barreras auditivas, atencionales, lingüísticas o de comprensión de vocabulario técnico. Además, se vincula con principios de mejora continua y satisfacción de estudiantes presentes en ISO 21001, al proponer una intervención medible, ajustable y replicable.",
  ],
  [
    "Problema:",
    "En clases técnicas, una parte importante del aprendizaje ocurre por vía oral: instrucciones, ejemplos, advertencias, correcciones y vocabulario especializado. Cuando esa información no se percibe o procesa oportunamente, se generan brechas de comprensión, participación y seguimiento de actividades. Esto puede afectar a estudiantes con dificultades auditivas, atencionales, lingüísticas, ubicación desfavorable en sala o necesidad de más tiempo para procesar conceptos técnicos. El problema no depende solo de la existencia de estudiantes sordos, sino de que la clase tradicional entrega demasiada información crítica en un único canal: la escucha inmediata.",
  ],
  [
    "Público Objetivo",
    "El proyecto se orienta a 15 estudiantes de la asignatura TPE401 Taller de proyecto de especialidad, de AIEP sede Osorno, durante el segundo semestre 2026. La implementación se realizará con participación de Diego Matías Obando Aguilera y la profesora Paulina María Beltrán Cerda, incorporando una aplicación disciplinar vinculada a clases técnicas donde el docente comunica procedimientos, instrucciones de seguridad, conceptos especializados o demostraciones que requieren alta precisión. El subtitulado beneficia a todo el curso porque transforma la explicación oral en una vía visual complementaria y accesible.",
  ],
  [
    "propuesta:",
    "Aula Subtitulada AIEP implementará un piloto pedagógico de subtitulado flotante en tiempo real. El docente usará su celular como micrófono y el computador recibirá la transcripción para mostrar subtítulos visibles sobre presentaciones, navegador, aula virtual u otro software de clase. El piloto incluirá diagnóstico breve, compra de kit institucional de audio, sesiones de uso, retroalimentación de estudiantes, ajustes técnicos y una guía de replicabilidad. Con autorización, parte de la transcripción podrá tratarse como insumo para generar material complementario, como resúmenes, glosarios o infografías. La iniciativa nace de una oportunidad identificada por Don Elías Silva y desarrollada técnicamente por Diego Obando.",
  ],
  [
    "Objetivo General del proyecto:",
    "Implementar y evaluar un piloto de subtitulado flotante en tiempo real para mejorar el acceso, comprensión y participación de estudiantes en clases técnicas de AIEP Osorno.",
  ],
  ["Objetivo específico 1:", "Implementar el piloto en TPE401 Taller de proyecto de especialidad durante el segundo semestre 2026, con 15 estudiantes participantes."],
  ["Objetivo específico 2:", "Evaluar percepción de utilidad, comprensión y participación estudiantil durante el uso del subtitulado."],
  ["Objetivo específico 3 (opcional):", "Ajustar el prototipo y el kit de audio según evidencia recogida en aula."],
  ["Objetivo específico 4 (opcional):", "Elaborar una guía breve para replicar la experiencia en otras asignaturas o sedes."],
  [
    "Resultados esperados:",
    "Se espera contar con un piloto implementado en aula, con estudiantes utilizando subtítulos en tiempo real como apoyo visual a la explicación docente. Se espera mejorar la percepción de claridad de instrucciones y conceptos técnicos, especialmente cuando la información oral es extensa o compleja. También se espera validar un kit mínimo de audio y operación que quede disponible para la sede, levantar retroalimentación estudiantil y docente, ajustar el prototipo y producir una guía breve de uso y replicabilidad. Como resultado complementario, se probará el uso autorizado de transcripciones para elaborar recursos de estudio, tales como resúmenes, glosarios o infografías.",
  ],
  [
    "Experiencias similares",
    "Existen soluciones de subtitulado automático en plataformas de videoconferencia, herramientas de transcripción y tecnologías de apoyo para accesibilidad. Sin embargo, suelen depender de una plataforma específica o de clases remotas. Esta propuesta se diferencia al llevar el subtitulado a la clase presencial o híbrida mediante un overlay visible sobre cualquier recurso de enseñanza. En AIEP han existido proyectos históricos vinculados a tecnologías educativas, NEE y equipamiento aplicado al aula; esta iniciativa toma esa línea y la enfoca en accesibilidad comunicacional en tiempo real, con bajo costo y posibilidad de transferencia.",
  ],
  [
    "Factor innovador:",
    "El factor innovador está en transformar la voz del docente en un recurso visual inmediato, visible sobre cualquier material de clase, sin exigir equipamiento especializado al estudiante. La IA no es el centro, sino un medio para reducir barreras de acceso al contenido oral. A diferencia de una transcripción posterior, el subtitulado ocurre durante la explicación y puede apoyar decisiones del estudiante en el momento: seguir instrucciones, revisar vocabulario, formular preguntas o confirmar pasos. Además, el proyecto integra equipamiento institucional de audio, evaluación pedagógica, resguardo de privacidad y producción de una guía transferible.",
  ],
  [
    "PROYECCIÓN INSTITUCIONAL Y ESCALABILIDAD",
    "El proyecto tiene alta transferibilidad porque el flujo de uso es simple: docente abre la app, escanea QR con celular, habla y proyecta subtítulos. El kit de micrófonos y accesorios quedará como capacidad instalada de AIEP Osorno para futuras asignaturas. La guía de replicabilidad incluirá configuración, protocolo de uso, recomendaciones de audio, resguardos de privacidad e instrumentos de evaluación. Puede adaptarse a carreras con vocabulario técnico, instrucciones críticas o demostraciones prácticas, y escalar a otras sedes con bajo costo relativo.",
  ],
  [
    "¿Cómo transforma este proyecto el rol de la/el estudiante en el aula?",
    "El estudiante deja de depender exclusivamente de la escucha inmediata y cuenta con una segunda vía de acceso al contenido. Esto le permite contrastar lo que oye con lo que lee, recuperar instrucciones, identificar vocabulario técnico y formular preguntas más precisas. La clase se vuelve más participativa porque el estudiante puede seguir el ritmo con mayor autonomía y menos temor a pedir repetición frente al grupo. Si se autoriza el registro textual, también puede acceder a materiales complementarios construidos desde la clase real, fortaleciendo estudio, revisión y evaluación.",
  ],
  [
    "Bidireccionalidad del proyecto",
    "N/A. La postulación corresponde a la línea Innovación en el aula.",
  ],
  [
    "que diferencian el proyecto que postula de la iniciativa original",
    "N/A. La postulación no corresponde a la línea Escalamiento.",
  ],
  [
    "Sede de implementación del proyecto a escalar",
    "N/A. La postulación no corresponde a la línea Escalamiento.",
  ],
  [
    "Contribución al enfoque integral de innovación con equidad:",
    "El proyecto contribuye al enfoque de innovación con equidad al reducir barreras de acceso a la información oral sin separar ni etiquetar estudiantes. El subtitulado se ofrece a todo el curso como apoyo universal, favoreciendo inclusión, diversidad de ritmos de comprensión y participación. La propuesta considera diagnóstico, retroalimentación estudiantil, ajustes y evaluación de utilidad, lo que permite observar si la herramienta mejora la experiencia de aprendizaje. También aporta a la equidad institucional al dejar equipamiento y una guía replicable para que otros docentes puedan aplicar la metodología.",
  ],
  [
    "Equipo:",
    "Diego Matías Obando Aguilera es docente de la Escuela de Ingeniería, Energía y Tecnología de AIEP Osorno y desarrollador del prototipo funcional de Aula Subtitulada AIEP. Cuenta con experiencia en programación web, desarrollo de aplicaciones y docencia técnica, lo que permite articular solución tecnológica y uso pedagógico. Paulina María Beltrán Cerda se incorpora como integrante docente para aplicar la experiencia en TPE401 Taller de proyecto de especialidad, con 15 estudiantes participantes. Don Elías Silva, Jefe de Escuela, impulsó institucionalmente la idea y apoyará la coordinación académica.",
  ],
]);

const indicators = [
  ["Sesiones piloto", "Cantidad de clases en que se utiliza subtitulado en tiempo real con kit de audio institucional.", "N° sesiones realizadas", "4 sesiones", "1"],
  ["Participación estudiantil", "Cantidad de estudiantes que participan en la experiencia y responden instrumento de percepción.", "N° estudiantes participantes", "15 o más", "1"],
  ["Utilidad percibida", "Porcentaje de estudiantes que declara que los subtítulos apoyan comprensión de instrucciones o conceptos.", "(respuestas positivas/total)*100", "70% o más", "2"],
  ["Guía replicable", "Documento breve con protocolo, resguardos, configuración y recomendaciones para otros docentes.", "Guía terminada", "1 guía", "4"],
];

const gantt = [
  ["Diagnóstico y coordinación", "Coordinar implementación con docentes, estudiantes y condiciones de aula.", "22/07/2026", "31/07/2026", "1"],
  ["Compra y preparación", "Adquirir kit institucional de audio docente, accesorios y preparar pruebas de aula.", "01/08/2026", "28/08/2026", "1"],
  ["Ajuste técnico", "Probar captura, subtítulos, privacidad y registro autorizado.", "17/08/2026", "04/09/2026", "3"],
  ["Implementación piloto", "Aplicar subtitulado en clases seleccionadas.", "07/09/2026", "02/10/2026", "1"],
  ["Evaluación", "Aplicar encuesta y recoger retroalimentación docente/estudiantil.", "28/09/2026", "09/10/2026", "2"],
  ["Material complementario", "Probar uso autorizado de transcripción para resumen/glosario/infografía.", "05/10/2026", "16/10/2026", "3"],
  ["Guía y socialización", "Elaborar guía, sistematizar aprendizajes y socializar resultados.", "19/10/2026", "04/11/2026", "4"],
];

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

function xmlEscape(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function xmlDecode(value) {
  return value
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", '"')
    .replaceAll("&amp;", "&");
}

function textOf(xml) {
  return [...xml.matchAll(/<w:t[^>]*>([\s\S]*?)<\/w:t>/g)].map((match) => xmlDecode(match[1])).join("");
}

function valueRun(value) {
  return `<w:r><w:rPr><w:rFonts w:ascii="Verdana" w:eastAsia="Calibri" w:hAnsi="Verdana" w:cs="Arial"/><w:szCs w:val="22"/><w:lang w:val="es-CL"/></w:rPr><w:t>${xmlEscape(value)}</w:t></w:r>`;
}

function setSecondCell(row, value) {
  const cells = row.match(/<w:tc[\s\S]*?<\/w:tc>/g);
  if (!cells || cells.length < 2) return row;
  const newCell = cells[1].replace(/(<w:p\b[\s\S]*?)(<\/w:p>)/, (_match, prefix, suffix) => `${prefix}${valueRun(value)}${suffix}`);
  return row.replace(cells[1], newCell);
}

function fillRows(xml) {
  return xml.replace(/<w:tr\b[\s\S]*?<\/w:tr>/g, (row) => {
    const rowText = textOf(row).replace(/\s+/g, " ").trim();
    for (const [label, value] of values.entries()) {
      if (rowText === label || rowText.includes(label)) {
        return setSecondCell(row, value);
      }
    }
    return row;
  });
}

function setCellText(cell, value) {
  const withPr = cell.replace(
    /(<w:p\b[\s\S]*?<w:pPr>[\s\S]*?<\/w:pPr>)([\s\S]*?)(<\/w:p>)/,
    (_match, prefix, _content, suffix) => `${prefix}${valueRun(value)}${suffix}`,
  );
  if (withPr !== cell) return withPr;
  return cell.replace(/(<w:p\b[^>]*>)([\s\S]*?)(<\/w:p>)/, (_match, prefix, _content, suffix) => `${prefix}${valueRun(value)}${suffix}`);
}

function fillFirstCellAfter(xml, label, value) {
  const labelIndex = xml.indexOf(label);
  if (labelIndex < 0) {
    console.warn(`Label not found: ${label}`);
    return xml;
  }
  const tableStart = xml.indexOf("<w:tbl", labelIndex);
  if (tableStart < 0) {
    console.warn(`Table not found after: ${label}`);
    return xml;
  }
  const tableEnd = xml.indexOf("</w:tbl>", tableStart);
  if (tableEnd < 0) return xml;
  const table = xml.slice(tableStart, tableEnd + "</w:tbl>".length);
  const cells = table.match(/<w:tc[\s\S]*?<\/w:tc>/g);
  if (!cells || cells.length === 0) return xml;
  const updatedTable = table.replace(cells[0], setCellText(cells[0], value));
  return xml.slice(0, tableStart) + updatedTable + xml.slice(tableEnd + "</w:tbl>".length);
}

function fillSingleAnswerTables(xml) {
  for (const [label, answer] of answers.entries()) {
    xml = fillFirstCellAfter(xml, label, answer);
  }
  return xml;
}

function fillStructuredTableAfter(xml, label, rowsData) {
  const labelIndex = xml.indexOf(label);
  if (labelIndex < 0) {
    console.warn(`Structured label not found: ${label}`);
    return xml;
  }
  const tableStart = xml.indexOf("<w:tbl", labelIndex);
  const tableEnd = xml.indexOf("</w:tbl>", tableStart);
  if (tableStart < 0 || tableEnd < 0) return xml;
  const table = xml.slice(tableStart, tableEnd + "</w:tbl>".length);
  const rows = table.match(/<w:tr\b[\s\S]*?<\/w:tr>/g);
  if (!rows || rows.length < 2) return xml;

  let updatedTable = table;
  rowsData.forEach((rowData, index) => {
    const row = rows[index + 1];
    if (!row) return;
    const cells = row.match(/<w:tc[\s\S]*?<\/w:tc>/g);
    if (!cells) return;
    let updatedRow = row;
    rowData.forEach((value, cellIndex) => {
      if (!cells[cellIndex]) return;
      updatedRow = updatedRow.replace(cells[cellIndex], setCellText(cells[cellIndex], value));
    });
    updatedTable = updatedTable.replace(row, updatedRow);
  });
  return xml.slice(0, tableStart) + updatedTable + xml.slice(tableEnd + "</w:tbl>".length);
}

function markClassroomInnovation(xml) {
  xml = xml.replace(
    /(<w:name w:val="Marcar1"\/><w:enabled\/><w:calcOnExit w:val="0"\/><w:checkBox><w:sizeAuto\/>)<w:default w:val="0"\/>/,
    "$1<w:default w:val=\"1\"/><w:checked/>",
  );
  return xml.replace(
    '<w:t xml:space="preserve"> Innovación en el aula</w:t>',
    '<w:t xml:space="preserve"> [X] Innovación en el aula</w:t>',
  );
}

const files = parseZip(fs.readFileSync(source));
const document = files.find((file) => file.name === "word/document.xml");
if (!document) throw new Error("word/document.xml not found");

let xml = document.data.toString("utf8");
xml = fillRows(xml);
xml = markClassroomInnovation(xml);
xml = fillSingleAnswerTables(xml);
xml = fillStructuredTableAfter(xml, "Indicadores:", indicators);
xml = fillStructuredTableAfter(xml, "Carta Gantt", gantt);
document.data = Buffer.from(xml, "utf8");

fs.writeFileSync(target, makeZip(files));
console.log(target);
