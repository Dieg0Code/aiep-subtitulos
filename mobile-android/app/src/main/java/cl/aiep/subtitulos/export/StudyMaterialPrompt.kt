package cl.aiep.subtitulos.export

object StudyMaterialPrompt {
    fun build(sessionName: String, transcriptMarkdown: String): String = """
        Convierte esta transcripción cruda de una clase presencial en un material de estudio claro.

        Reglas estrictas:
        - Conserva el hilo, tono, ejemplos, énfasis y vocabulario del docente.
        - Corrige errores evidentes de reconocimiento, puntuación y cortes de frase.
        - No inventes datos, objetivos, ejemplos, tareas ni definiciones que no aparezcan.
        - Si algo es ambiguo, mantenlo sobrio o marca que no queda claro.
        - Quita marcas de tiempo cuando estorben, pero respeta el orden de la clase.
        - Escribe en español de Chile, formal pero cercano.

        Estructura de salida en Markdown:
        # $sessionName

        ## Resumen breve
        1 párrafo corto.

        ## Ideas principales
        - Lista breve.

        ## Desarrollo de la clase
        Texto ordenado por temas, fiel a lo dicho.

        ## Conceptos y definiciones
        Solo si aparecen en la clase.

        ## Dudas, tareas o acuerdos
        Solo si aparecen en la clase.

        Transcripción cruda:
        $transcriptMarkdown
    """.trimIndent()
}
