// SimpleCalcGUI.java
package simplecalc;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleCalcGUI extends JFrame {
    private JTextArea inputArea;
    private JTextArea outputArea; // Para tokens y errores
    private JLabel statusLabel;
    private Highlighter.HighlightPainter errorPainter;

    public SimpleCalcGUI() {
        setTitle("SimpleCalc IDE");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        errorPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.PINK);
    }

    private void initComponents() {
        // Paneles
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel centerPanel = new JPanel(new GridLayout(1, 1)); // Para salida
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Componentes
        inputArea = new JTextArea();
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setBorder(BorderFactory.createTitledBorder("Código SimpleCalc"));

        outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Salida del Compilador"));


        JButton processButton = new JButton("Procesar Código");
        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processCode();
            }
        });

        statusLabel = new JLabel("Listo.");

        // Layout
        topPanel.add(inputScrollPane, BorderLayout.CENTER);
        topPanel.add(processButton, BorderLayout.SOUTH);

        centerPanel.add(outputScrollPane);

        bottomPanel.add(statusLabel);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Preferencias de tamaño para las áreas de texto
        inputScrollPane.setPreferredSize(new Dimension(780, 200));
        outputScrollPane.setPreferredSize(new Dimension(780, 300));


        setContentPane(mainPanel);
    }

    private void processCode() {
        String sourceCode = inputArea.getText();
        outputArea.setText(""); // Limpiar salida anterior
        inputArea.getHighlighter().removeAllHighlights(); // Limpiar resaltados de error anteriores

        // 1. Análisis Léxico
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();
        
         System.out.println("----- TOKENS DEL LEXER (Total: " + tokens.size() + ") -----");

        StringBuilder sb = new StringBuilder();
        sb.append("--- Tokens Reconocidos ---\n");
        sb.append(Token.getTableHeader()).append("\n");
        for (Token token : tokens) {
            sb.append(token.toString()).append("\n");
        }
        sb.append(Token.getTableFooter()).append("\n\n");

        // Filtrar tokens de error léxico para mostrar en la lista de errores
        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, t.lexeme))
                                           .collect(Collectors.toList());

        // 2. Análisis Sintáctico (y Semántico Básico)
        Parser parser = new Parser(tokens); // Pasamos solo los tokens no-error o todos? El parser debería saber saltar errores
        boolean syntaxValid = parser.parse();
        List<String> syntaxAndSemanticErrors = parser.getErrors();


        // 3. Mostrar Resultados
        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
            }
            sb.append("\n");
        }

        if (!syntaxAndSemanticErrors.isEmpty()) {
            sb.append("--- Errores Sintácticos/Semánticos Detectados ---\n");
            for (String err : syntaxAndSemanticErrors) {
                sb.append(err).append("\n");
                // Intentar resaltar el error en el inputArea
                // Esto requiere parsear la línea y columna del mensaje de error
                 try {
                    // Formato esperado: "[Línea L, Col C] Error..."
                    if (err.startsWith("[")) {
                        String locationPart = err.substring(1, err.indexOf("]"));
                        String[] parts = locationPart.split(",");
                        int line = Integer.parseInt(parts[0].replace("Línea ", "").trim());
                        int col = Integer.parseInt(parts[1].replace("Col ", "").trim());
                        highlightError(line, col);
                    }
                } catch (Exception ex) {
                    // No se pudo parsear la ubicación del error del mensaje
                    System.err.println("Error al intentar resaltar: " + ex.getMessage());
                }
            }
            sb.append("\n");
        }


        if (lexicalErrors.isEmpty() && syntaxValid) {
            sb.append(">>> El código es léxica y sintácticamente VÁLIDO según SimpleCalc. <<<\n");
            statusLabel.setText("Resultado: VÁLIDO.");
            statusLabel.setForeground(new Color(0, 128, 0)); // Verde
        } else {
            sb.append(">>> El código contiene errores. <<<\n");
            statusLabel.setText("Resultado: INVÁLIDO.");
            statusLabel.setForeground(Color.RED);
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0); // Scroll al inicio
    }
    
    private void highlightError(int line, int col) {
        try {
            // Las líneas/columnas son 1-based, JTextArea es 0-based
            int docLine = line - 1;
            int docCol = col - 1;

            int startOffset = inputArea.getLineStartOffset(docLine) + docCol;
            // Resaltar un solo carácter o una pequeña región.
            // Para hacerlo más útil, necesitaríamos el Token real del error y su longitud.
            // Por ahora, resaltamos un solo carácter en la posición.
            int endOffset = startOffset + 1; // Resaltar solo un caracter por simplicidad.
                                            // Idealmente, si el token tiene longitud, sería startOffset + token.lexeme.length()
            
            // Asegurarse que endOffset no exceda la longitud del texto o la línea
            if (startOffset < inputArea.getDocument().getLength()) {
                 endOffset = Math.min(endOffset, inputArea.getDocument().getLength());
                 endOffset = Math.min(endOffset, inputArea.getLineEndOffset(docLine)); //No pasar de la linea

                 if(startOffset < endOffset){ //Asegurar que el rango sea valido
                     inputArea.getHighlighter().addHighlight(startOffset, endOffset, errorPainter);
                 } else if (startOffset == endOffset && startOffset < inputArea.getDocument().getLength()){ //Si solo un char y no es el final
                     inputArea.getHighlighter().addHighlight(startOffset, startOffset +1, errorPainter);
                 }
            }

        } catch (BadLocationException ex) {
            System.err.println("Error al resaltar: No se pudo obtener la ubicación " + line + "," + col + ". Detalle: " + ex.getMessage());
        }
    }


    public static void main(String[] args) {
        // Para mejor look & feel en algunos sistemas
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SimpleCalcGUI().setVisible(true);
            }
        });
    }
}