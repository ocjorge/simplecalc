// Token.java
package simplecalc; // Sugiero usar un paquete

public class Token {
    public enum TokenType {
        // Palabras Reservadas
        OPERACION_KEYWORD, ENTRADA_KEYWORD, SALIDA_KEYWORD, SI_KEYWORD,
        // Ya no hay ENTONCES_KEYWORD

        // Identificadores y Literales
        ID, NUMERO_ENTERO, CADENA_LITERAL,

        // Operadores Aritméticos
        OP_SUMA, OP_RESTA, OP_MULT, OP_DIV,

        // Operadores Relacionales (solo los válidos)
        OP_MENOR, OP_MAYOR, OP_IGUAL_IGUAL, // << SOLO ESTOS

        // Operador de Asignación
        ASIGNACION, // '='

        // Delimitadores
        PAREN_IZQ, PAREN_DER,
        LLAVE_IZQ, LLAVE_DER,
        PUNTO,
        EOL,        // <<-- NUEVO TOKEN para End Of Line
        // Especiales
        WHITESPACE, // Para ser ignorado por el parser
        ERROR,      // Para errores léxicos
        EOF         // End Of File/Input
    }

    public final TokenType type;
    public final String lexeme;
    public final Object literal; // Para números o cadenas, si se convierten en el lexer
    public final int line;
    public final int column;

    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        // Para la tabla de tokens
        String literalStr = (literal != null) ? literal.toString() : "";
        return String.format("| %-25s | %-20s | %-15s | %4d | %4d |",
                type, lexeme, literalStr, line, column);
    }

    // Método para la visualización de la tabla, similar al tuyo
    public static String getTableHeader() {
        return "+---------------------------+----------------------+-----------------+------+------+\n" +
               "| Tipo de Token             | Lexema               | Literal         | Line | Col  |\n" +
               "+---------------------------+----------------------+-----------------+------+------+";
    }

    public static String getTableFooter() {
        return "+---------------------------+----------------------+-----------------+------+------+";
    }
}