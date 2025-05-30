// Parser.java (TU CÓDIGO ORIGINAL con correcciones)
package simplecalc;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import static simplecalc.Token.TokenType.*;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private List<String> errors = new ArrayList<>();
    private Set<String> declaredVariables = new HashSet<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean parse() {
        current = 0;
        errors.clear();
        declaredVariables.clear();
        try {
            programa();
            // Esta verificación de EOF es la que causa problemas si 'programa' no la maneja perfectamente.
            // La regla 'programa' debe consumir el EOF. Si no lo hace, es un error en 'programa'.
            // if (!isAtEnd() && peek().type != EOF) { // Comentado temporalmente para enfocarnos
            //      if (peek().type != EOF) {
            //          error(peek(), "Tokens inesperados después del final del programa.",
            //                "Se esperaba el fin de la entrada después de la estructura principal del programa.");
            //      }
            // }
        } catch (SyntaxError | SemanticError e) {
            return false;
        }
        return errors.isEmpty();
    }

    private void programa() {
        consume(OPERACION_KEYWORD, "Se esperaba 'OPERACION' al inicio del programa.");
        consumeOptionalEOLs();
        consume(ENTRADA_KEYWORD, "Se esperaba 'ENTRADA' después de 'OPERACION'.");
        consumeOptionalEOLs();
        consume(LLAVE_IZQ, "Se esperaba '{' después de 'ENTRADA'.");
        // cuerpo_programa ya maneja EOLs internos
        cuerpo_programa();
        consume(LLAVE_DER, "Se esperaba '}' para cerrar el cuerpo del programa.");
        consumeOptionalEOLs();
        consume(SALIDA_KEYWORD, "Se esperaba 'SALIDA' al final del programa.");
        consumeOptionalEOLs(); // EOLs antes del EOF
        if (!check(EOF)) {
            error(peek(), "Error al final de la entrada.",
                    "Falta el token de fin de archivo (EOF) o hay tokens extra.");
        } else {
            consume(EOF, "Se esperaba el fin de la entrada después de 'SALIDA'.");
        }
    }

        private void cuerpo_programa() {
        consumeOptionalEOLs();
        while (!check(LLAVE_DER) && !isAtEnd()) { // Condición del bucle
            if (peek().type == ERROR) {
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s. Se ignora.",
                           peek().line, peek().column, peek().lexeme));
                advance(); // Consume el token ERROR
                continue;  // Vuelve al inicio del while
            }
            if (peek().type == EOL) {
                advance(); // Consume el token EOL
                continue;  // Vuelve al inicio del while
            }
            // Si no es ERROR ni EOL, y no es LLAVE_DER ni EOF, debe ser una sentencia
            sentencia();
            // IMPORTANTE: Si sentencia() encuentra un error y llama a synchronizeToStatementBoundary(),
            // y la sincronización consume tokens, debemos asegurarnos que 'current' haya avanzado.
            // Si sentencia() simplemente retorna sin consumir un token (porque no reconoció nada
            // y no hay un 'else' final que lance error y sincronice), ENTONCES HAY BUCLE INFINITO.
            // La sentencia() corregida arriba ahora SIEMPRE o consume una sentencia válida,
            // o lanza un error y sincroniza (que consume tokens), o no hace nada si el token
            // es LLAVE_DER, EOF, o EOL (que son manejados por este bucle while).
        }
        consumeOptionalEOLs();
    }

       private void sentencia() {
        System.out.println("DEBUG: Entrando a sentencia(), peek()=" + peek().type + ", lexema='" + peek().lexeme + "'"); // DEBUG

        if (check(ID)) { // Si el token actual es un ID
            // Necesitamos mirar adelante de forma segura para ver si es una asignación
            if (current + 1 < tokens.size() && tokens.get(current + 1).type == ASIGNACION) {
                System.out.println("DEBUG:  sentencia: ID seguido de ASIGNACION -> asignacion_stmt()"); // DEBUG
                asignacion_stmt();
            } else {
                // Es un ID, pero no seguido de ASIGNACION. Esto es un error en SimpleCalc.
                System.out.println("DEBUG:  sentencia: ID no seguido de ASIGNACION -> error"); // DEBUG
                error(peek(), "Sentencia inválida comenzando con ID '" + peek().lexeme + "'.",
                        "Un identificador debe ser parte de una asignación (ej: ID = valor.).");
                synchronizeToStatementBoundary(); // Intentar recuperar
            }
        } else if (check(ENTRADA_KEYWORD)) {
            System.out.println("DEBUG:  sentencia: ENTRADA_KEYWORD -> entrada_stmt()"); // DEBUG
            entrada_stmt();
        } else if (check(SALIDA_KEYWORD)) {
            System.out.println("DEBUG:  sentencia: SALIDA_KEYWORD -> salida_stmt()"); // DEBUG
            salida_stmt();
        } else if (check(SI_KEYWORD)) {
            System.out.println("DEBUG:  sentencia: SI_KEYWORD -> si_stmt()"); // DEBUG
            si_stmt();
        }
        // Si hemos llegado aquí, el token actual no es un inicio válido de sentencia
        // Y tampoco es LLAVE_DER, EOF, o EOL (esos los maneja cuerpo_programa)
        // entonces es un error.
        // PERO, cuerpo_programa ya tiene un chequeo para EOL.
        // Así que aquí solo nos preocupamos si NO es un token de fin de bloque.
        else if (peek().type != LLAVE_DER && peek().type != EOF && peek().type != EOL && peek().type != ERROR) {
            // El chequeo de peek().type != ERROR es para no reportar doble error si el lexer ya lo hizo.
            // El ERROR es manejado por cuerpo_programa.
            System.out.println("DEBUG:  sentencia: Token inesperado " + peek().type + " -> error de sentencia no reconocida"); // DEBUG
            error(peek(), "Sentencia inválida o no reconocida.",
                    "Se esperaba 'ENTRADA', 'SALIDA', 'SI', una asignación (ID = ...), o fin de bloque '}'.");
            synchronizeToStatementBoundary();
        }
        // Si es LLAVE_DER, EOF, o EOL, o ERROR, sentencia() simplemente retorna,
        // y cuerpo_programa() manejará esos casos (terminar bucle, consumir EOL/ERROR y continuar).
        System.out.println("DEBUG: Saliendo de sentencia(), current ahora apunta a: " + (isAtEnd() ? "EOF" : peek().type)); // DEBUG
    }

    // Las reglas de sentencia deben ahora ser estrictas con el PUNTO.
    private void asignacion_stmt() {
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable para la asignación.");
        consume(ASIGNACION, "Se esperaba '=' después del nombre de variable '" + varNameToken.lexeme + "'.");
        expresion_aritmetica(); // expresion_aritmetica NO debe consumir un EOL si está incompleta
        consume(PUNTO, "Se esperaba '.' para terminar la sentencia de asignación.");
        // Opcionalmente, consumir EOLs después de una sentencia completa
        
         // -------> DEBUG <---------
    System.out.println("DEBUG asignacion_stmt: Antes de add, varNameToken.lexeme = " + varNameToken.lexeme);
    System.out.println("DEBUG asignacion_stmt: declaredVariables ANTES: " + declaredVariables);
    // -------> FIN DEBUG <---------

    declaredVariables.add(varNameToken.lexeme); // Marcar como inicializada (semántico)

    // -------> DEBUG <---------
    System.out.println("DEBUG asignacion_stmt: declaredVariables DESPUÉS de add '" + varNameToken.lexeme + "': " + declaredVariables);
    // -------> FIN DEBUG <---------
        consumeOptionalEOLs();
    }

    // ... (entrada_stmt, salida_stmt, valor_salida se mantienen como en TU original)
    private void entrada_stmt() {
        consume(ENTRADA_KEYWORD, "Error interno: Se esperaba 'ENTRADA' para entrada_stmt.");
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable después de 'ENTRADA'.");
        consume(PUNTO, "Se esperaba '.' para terminar la sentencia 'ENTRADA'.");
        consumeOptionalEOLs();
    }

    private void salida_stmt() {
        consume(SALIDA_KEYWORD, "Error interno: Se esperaba 'SALIDA' para salida_stmt.");
        valor_salida();
        consume(PUNTO, "Se esperaba '.' para terminar la sentencia 'SALIDA'.");
        consumeOptionalEOLs();
    }

    private void valor_salida() {
        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, ""); // Mensaje no necesario si el chequeo ya lo hizo
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else if (check(CADENA_LITERAL)) {
            consume(CADENA_LITERAL, "");
        } else {
            error(peek(), "Valor inválido para 'SALIDA'.",
                    "Se esperaba un ID, un número entero o una cadena literal después de 'SALIDA'.");
        }
    }

    // ... (si_stmt y sus componentes se mantienen como en TU original)
    private void si_stmt() {
        consume(SI_KEYWORD, "Error interno: Se esperaba 'SI' para si_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'SI'.");
        condicion_simple();
        consume(PAREN_DER, "Se esperaba ')' después de la condición en 'SI'.");
        accion_unica_si();
    }

    private void condicion_simple() {
        operando_condicion();
        operador_relacional();
        operando_condicion();
    }

    private void operando_condicion() {
        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else {
            error(peek(), "Operando inválido en condición.",
                    "Se esperaba un ID o un número entero en la condición.");
        }
    }

    private void operador_relacional() {
        if (check(OP_MENOR) || check(OP_MAYOR) || check(OP_IGUAL_IGUAL)) {
            advance();
        } else {
            error(peek(), "Operador relacional inválido.",
                    "Se esperaba '<', '>' o '=='.");
        }
    }

    // CORRECCIÓN EN ACCION_UNICA_SI para predicción de asignación
    // En accion_unica_si, la parte de SALIDA también necesita punto.
    private void accion_unica_si() {
        if (check(SALIDA_KEYWORD)) {
            consume(SALIDA_KEYWORD, "");
            valor_salida();
            consume(PUNTO, "Se esperaba '.' para terminar la acción 'SALIDA' dentro del 'SI'.");
            // Aquí no consumimos EOLs opcionales porque la estructura del SI es más rígida
            // y no es una lista de sentencias de alto nivel.
        } else if (check(ID) && (current + 1 < tokens.size() && tokens.get(current + 1).type == ASIGNACION)) {
            asignacion_stmt(); // asignacion_stmt ya maneja sus EOLs opcionales
        } else {
            error(peek(), "Acción inválida después de 'SI (...)'.",
                    "Se esperaba una sentencia 'SALIDA ...' o una asignación 'ID = ...'.");
        }
    }

    // Método para consumir EOLs opcionales entre sentencias o al final de bloques.
    private void consumeOptionalEOLs() {
        while (match(EOL)) {
            // Seguir consumiendo EOLs
        }
    }

    // ... (expresion_aritmetica, termino, factor se mantienen como en TU original)
    // Expresiones aritméticas y sus componentes (termino, factor)
    // deben ser sensibles a EOLs inesperados.
    private void expresion_aritmetica() {
        termino();
        while (match(OP_SUMA, OP_RESTA)) {
            if (peek().type == EOL) {
                throw error(peek(), "Expresión incompleta antes de salto de línea.",
                        "Se esperaba un operando después de '" + previous().lexeme + "' pero se encontró un salto de línea.");
            }
            termino();
        }
    }

    private void termino() {
        factor();
        while (match(OP_MULT, OP_DIV)) {
            if (peek().type == EOL) {
                throw error(peek(), "Expresión incompleta antes de salto de línea.",
                        "Se esperaba un operando después de '" + previous().lexeme + "' pero se encontró un salto de línea.");
            }
            factor();
        }
    }

    private void factor() {
        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else if (check(PAREN_IZQ)) {
            consume(PAREN_IZQ, "");
            expresion_aritmetica();
            consume(PAREN_DER, "Se esperaba ')' para cerrar la expresión entre paréntesis.");
        } else {
            error(peek(), "Expresión aritmética malformada.",
                    "Se esperaba un ID, un número, o una expresión entre paréntesis '(...)'.");
        }
    }

    // ---- Métodos de ayuda del Parser (TU CÓDIGO ORIGINAL) ----
    private Token consume(Token.TokenType type, String message) {
        // Si esperamos un tipo X, y encontramos EOL, es un tipo de error específico.
        if (peek().type == EOL && type != EOL && type != EOF /* y otros donde EOL es ok */) {
            throw error(peek(), "Salto de línea inesperado.",
                    "Se esperaba '" + type + "' para continuar/terminar la sentencia, pero se encontró un salto de línea. " + message);
        }
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message, message);
    }

    private SyntaxError error(Token token, String generalMessage, String specificMessageToUser) {
        SyntaxError e = new SyntaxError(token, generalMessage, specificMessageToUser);
        errors.add(e.getMessage());
        return e;
    }

    private SemanticError semanticError(Token token, String generalMessage, String specificMessageToUser) {
        SemanticError e = new SemanticError(token, generalMessage, specificMessageToUser);
        errors.add(e.getMessage());
        return e;
    }

    private boolean match(Token.TokenType... types) {
        for (Token.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(Token.TokenType type) {
        if (isAtEnd()) {
            // Si estamos al final (isAtEnd es true, lo que implica peek().type == EOF),
            // entonces check(EOF) debe ser true.
            // Para cualquier otro tipo, check(OTRO_TIPO) debe ser false.
            return type == EOF;
        }
        // Si no estamos al final, simplemente compara el tipo del token actual.
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    // isAtEnd() DEBE ser robusto
    private boolean isAtEnd() {
        // Estamos al final si el token actual que 'peek' vería es EOF.
        // Asumimos que 'peek()' es seguro y 'current' no se pasa de los límites
        // antes de esta llamada, o que peek() maneja el límite.
        // El lexer siempre añade EOF, por lo que siempre hay un token EOF para 'peek'.
        if (current >= tokens.size()) {
            // Esto no debería suceder si el lexer siempre agrega EOF
            // y el parser no avanza más allá del índice de EOF antes de esta verificación.
            // Pero como salvaguarda, si current se sale, considera que está "al final" lógicamente.
            System.err.println("ADVERTENCIA PARSER: isAtEnd() llamado con current fuera de límites.");
            return true; // O lanza una excepción si esto es un estado irrecuperable.
        }
        return peek().type == EOF; // La definición más directa
    }

    private Token peek() {
        if (current >= tokens.size()) {
            // Esto indica un error en la lógica del parser si current se pasa
            // del tamaño de la lista de tokens (que incluye EOF).
            // Devolver el último token (EOF) podría enmascarar el error, pero
            // evita un IndexOutOfBoundsException inmediato.
            System.err.println("ADVERTENCIA PARSER: peek() llamado con current (" + current + ") >= tokens.size() (" + tokens.size() + "). Devolviendo el último token.");
            return tokens.get(tokens.size() - 1); // Devolver el último token (que debe ser EOF)
        }
        return tokens.get(current);
    }

    private Token previous() {
        // Asegurarse de no ir antes del inicio
        if (current == 0) {
            return tokens.get(0); // O manejar de otra forma, pero no debería llamarse con current=0 si se usa bien
        }
        return tokens.get(current - 1);
    }

    private void checkVariableInitialized(Token name) {
        if (!declaredVariables.contains(name.lexeme)) {
            throw semanticError(name,
                    "Variable no inicializada: " + name.lexeme,
                    "La variable '" + name.lexeme + "' se usa antes de asignarle un valor.");
        }
    }

    // En Parser.java
private void synchronizeToStatementBoundary() {
    System.out.println("DEBUG: Entrando a synchronizeToStatementBoundary(), peek() al entrar=" + peek().type); // DEBUG
    advance(); // Consumir el token erróneo
    System.out.println("DEBUG:  synchronize: después de consumir token erróneo, peek()=" + peek().type); // DEBUG

    int recoveryLoopGuard = 0; // Para prevenir bucles infinitos teóricos
    final int MAX_RECOVERY_ATTEMPTS = tokens.size() + 5; // Un límite generoso

    while (!isAtEnd()) {
        System.out.println("DEBUG:  synchronize: en bucle, peek()=" + peek().type + ", previous()=" + previous().type); // DEBUG
        recoveryLoopGuard++;
        if (recoveryLoopGuard > MAX_RECOVERY_ATTEMPTS) {
            System.err.println("ERROR PARSER: Posible bucle infinito en synchronizeToStatementBoundary(). Abortando sincronización.");
            errors.add("[ERROR INTERNO] Falla en la recuperación de errores. Demasiados tokens consumidos.");
            // Forzar salida del bucle para evitar congelamiento real
            while(!isAtEnd()) advance(); // Consumir todo lo que queda
            return;
        }

        if (previous().type == PUNTO) {
            System.out.println("DEBUG:  synchronize: encontrado PUNTO en previous. Retornando."); // DEBUG
            return;
        }
        if (previous().type == EOL && peek().type != LLAVE_DER && peek().type != EOF) {
             // Si el anterior fue EOL, y el actual no es un terminador de bloque,
             // podríamos considerar esto un punto de sincronización si las sentencias
             // pueden estar separadas solo por EOL (aunque SimpleCalc requiere PUNTO).
             // Para SimpleCalc, esto probablemente no sea un buen punto de retorno si no hay PUNTO.
        }


        switch (peek().type) {
            case ENTRADA_KEYWORD:
            case SALIDA_KEYWORD:
            case SI_KEYWORD:
            // No añadir ID aquí como punto de sincronización porque podría ser el inicio de otro error
            case LLAVE_DER:
            case EOF: // Si llegamos a EOF, hemos terminado de sincronizar
                System.out.println("DEBUG:  synchronize: encontrado " + peek().type + ". Retornando."); // DEBUG
                return;
            default:
                // Sigue avanzando
        }
        System.out.println("DEBUG:  synchronize: avanzando desde " + peek().type); // DEBUG
        advance();
    }
    System.out.println("DEBUG: Salida de synchronizeToStatementBoundary() porque isAtEnd() es true."); // DEBUG
}
}
