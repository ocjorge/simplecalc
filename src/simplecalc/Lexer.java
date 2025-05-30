// Lexer.java
package simplecalc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;    // Inicio del lexema actual
    private int current = 0;  // Carácter actual que se está considerando
    private int line = 1;     // Línea actual para reporte de errores
    // 'column' ya no es un campo de clase, se calcula al añadir token
    // o se pasa/usa localmente. La columna del token es start - inicioDeLinea + 1.

    private static final Map<String, Token.TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("OPERACION", Token.TokenType.OPERACION_KEYWORD);
        keywords.put("ENTRADA", Token.TokenType.ENTRADA_KEYWORD);
        keywords.put("SALIDA", Token.TokenType.SALIDA_KEYWORD);
        keywords.put("SI", Token.TokenType.SI_KEYWORD);
        // "ENTONCES" eliminado
    }

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtLexerEnd()) { // Usa un isAtEnd específico para el lexer
            start = current; // Marcar inicio del lexema
            scanToken();
        }
        // Añadir token EOF al final
        // La línea es la 'line' actual (que pudo haber sido incrementada por un \n final)
        // La columna para EOF puede ser la columna después del último carácter real
        // o 1 si la última línea estaba vacía o terminó con \n.
        tokens.add(new Token(Token.TokenType.EOF, "", null, line, calculateColumnForCurrentPos(current)));
        return tokens;
    }

    private void scanToken() {
        char c = advanceLexerChar(); // Avanza el carácter y actualiza 'current'

        switch (c) {
            // Caracteres simples
            case '(': addToken(Token.TokenType.PAREN_IZQ); break;
            case ')': addToken(Token.TokenType.PAREN_DER); break;
            case '{': addToken(Token.TokenType.LLAVE_IZQ); break;
            case '}': addToken(Token.TokenType.LLAVE_DER); break;
            case '+': addToken(Token.TokenType.OP_SUMA); break;
            case '-': addToken(Token.TokenType.OP_RESTA); break;
            case '*': addToken(Token.TokenType.OP_MULT); break;
            case '.': addToken(Token.TokenType.PUNTO); break;

            // Operadores de uno o dos caracteres
            case '=':
                addToken(matchLexerChar('=') ? Token.TokenType.OP_IGUAL_IGUAL : Token.TokenType.ASIGNACION);
                break;
            case '<':
                if (matchLexerChar('=')) { // Si es '<='
                    // Error léxico: '<=' no está permitido
                    // Consumimos el '=', el lexema es "<="
                    addErrorToken("Operador relacional '<=' no está permitido. Use '<' o '=='.");
                } else {
                    addToken(Token.TokenType.OP_MENOR);
                }
                break;
            case '>':
                if (matchLexerChar('=')) { // Si es '>='
                    // Error léxico: '>=' no está permitido
                    addErrorToken("Operador relacional '>=' no está permitido. Use '>' o '=='.");
                } else {
                    addToken(Token.TokenType.OP_MAYOR);
                }
                break;
            case '/':
                // Por ahora solo división. Si añades comentarios, aquí iría la lógica.
                addToken(Token.TokenType.OP_DIV);
                break;

            // Espacios en blanco (Ignorar)
            case ' ':
            case '\r': // Retorno de carro usualmente va con \n o se trata similar
            case '\t':
                // Ignorar espacios y tabuladores (whitespace que no es EOL)
                // No se genera token, solo se avanza.
                break;
            case '\n':
                 // Generar un token EOL y luego incrementar la línea
                addToken(Token.TokenType.EOL, "\\n"); // Lexema podría ser útil para debug
                line++; // Incrementar línea
                // La columna se recalculará para el siguiente token basado en 'start' y el inicio de esta nueva línea.
                break;

            // Literales de cadena
            case '"':
                string();// string() debe manejar errores si encuentra EOL dentro
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlphaStart(c)) { // Debe ser letra mayúscula para inicio de ID
                    identifier();
                } else {
                    // Carácter no reconocido
                    // 'start' ya está en 'c', 'current' avanzó uno más allá de 'c'
                    // El lexema sería solo 'c'.
                    addErrorToken("Caracter inesperado: '" + c + "'");
                }
                break;
        }
    }

    private void identifier() {
        // El primer carácter (isAlphaStart) ya fue consumido por advanceLexerChar()
        // y está en source.charAt(start)
        while (isAlphaNumeric(peekLexerChar())) advanceLexerChar();

        String text = source.substring(start, current);
        Token.TokenType type = keywords.get(text);

        if (type == null) { // No es una palabra reservada, es un ID
            // La validación de [A-Z][A-Z0-9]* se hace parcialmente por isAlphaStart y isAlphaNumeric
            // No es necesario revalidar aquí si esas funciones son correctas.
            addToken(Token.TokenType.ID);
        } else { // Es una palabra reservada
            addToken(type);
        }
    }

    private void number() {
        // El primer dígito ya fue consumido por advanceLexerChar()
        while (isDigit(peekLexerChar())) advanceLexerChar();

        String numStr = source.substring(start, current);
        try {
            addToken(Token.TokenType.NUMERO_ENTERO, Integer.parseInt(numStr));
        } catch (NumberFormatException e) {
             addErrorToken("Número entero inválido o muy grande: '" + numStr + "'");
        }
    }
    
     // La función string() debe ser consciente de EOL:
    private void string() {
        while (peekLexerChar() != '"' && !isAtLexerEnd()) {
            // NO SE PUEDE USAR directament peekLexerChar() == '\n' porque el lexer ahora
            // debería procesar \n como un token EOL.
            // Si un \n interrumpe la cadena, es un error.
            // El lexer, al estar dentro de string(), si ve un \n, debe marcar error de cadena.
            // O, mejor: el bucle de string() sigue consumiendo caracteres.
            // Si el *carácter* actual es \n o \r, es un error DENTRO de la cadena.
            char peeked = peekLexerChar();
            if (peeked == '\n' || peeked == '\r') {
                 addErrorToken("Salto de línea o retorno de carro no permitido en cadena literal.");
                 // Aquí, la cadena está malformada. Podríamos no consumir el \n/ \r
                 // para que luego se tokenice como EOL si eso tiene sentido, o consumir
                 // y reportar la cadena como error.
                 // Es más simple considerar la cadena errónea y no preocuparse por un EOL posterior
                 // si la cadena ya falló.
                 // Para este caso, consumamos hasta el fin de la cadena malformada (o fin de línea).
                 while(peekLexerChar() != '"' && !isAtLexerEnd() && peekLexerChar() != '\n' && peekLexerChar() != '\r') {
                     advanceLexerChar();
                 }
                 // Si se detuvo por \n o \r, current apunta a él. El error ya está.
                 // Si se detuvo por ", current apunta a él.
                 // Si se detuvo por EOF, current apunta al final.
                 return; // Salir, el error de cadena ya fue (o será) añadido.
            }
            advanceLexerChar();
        }
        // ... (resto de la lógica de string para cerrar comillas y añadir token)
         if (isAtLexerEnd()) {
            addErrorToken("Cadena literal no terminada.");
            return;
        }
        advanceLexerChar(); // Consumir la comilla de cierre "
        String value = source.substring(start + 1, current - 1);
        addToken(Token.TokenType.CADENA_LITERAL, value);
    }
    // --- Métodos de ayuda del Lexer ---

    private boolean isAtLexerEnd() {
        return current >= source.length();
    }

    private char advanceLexerChar() {
        // Devuelve el carácter en 'current' y luego incrementa 'current'
        return source.charAt(current++);
    }

    private boolean matchLexerChar(char expected) {
        if (isAtLexerEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++; // Consumir si coincide
        return true;
    }

    private char peekLexerChar() {
        if (isAtLexerEnd()) return '\0'; // Carácter nulo para indicar fin
        return source.charAt(current);
    }

    // peekNext no se usa mucho en este lexer, pero podría ser útil
    // private char peekNextLexerChar() {
    //    if (current + 1 >= source.length()) return '\0';
    //    return source.charAt(current + 1);
    // }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlphaStart(char c) { // Solo mayúsculas para inicio de ID
        return (c >= 'A' && c <= 'Z');
    }

    private boolean isAlphaNumeric(char c) { // Mayúsculas y dígitos para el resto de ID
        return isAlphaStart(c) || isDigit(c); // isAlphaStart ya cubre A-Z
    }

    private void addToken(Token.TokenType type) {
        addToken(type, null);
    }

    private void addToken(Token.TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, calculateColumnForCurrentPos(start)));
    }
    
    private void addErrorToken(String message) {
        // El lexema del error es desde 'start' hasta 'current' (que ya avanzó)
        String problematicLexeme = source.substring(start, current);
        
        // El mensaje de error ya es específico. El lexema problemático se añade a la info del token.
        tokens.add(new Token(Token.TokenType.ERROR, problematicLexeme + " (" + message + ")",
                             null, line, calculateColumnForCurrentPos(start)));
        // No es necesario avanzar 'current' aquí porque scanToken() se llamará de nuevo
        // y 'current' ya está en la posición para el siguiente token o ya consumió lo problemático.
    }

    // Calcula la columna inicial de un token dado su 'start' en el 'source'
    private int calculateColumnForCurrentPos(int tokenStartIndex) {
        int col = 1;
        int currentLineActualStart = 0;
        // Buscar el inicio de la línea donde está tokenStartIndex
        for (int i = tokenStartIndex -1; i >= 0; i--) {
            if (source.charAt(i) == '\n') {
                currentLineActualStart = i + 1;
                break;
            }
        }
        col = tokenStartIndex - currentLineActualStart + 1;
        return col;
    }
}