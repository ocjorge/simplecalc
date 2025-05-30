// Main.java
/* Desarrollado por 
Hernández Díaz Mariana Sinaí
Martinez Hinostroza Andrea Marian
Ortiz Ceballos Jorge
Terron Mejía Ulises Bertín
Villavicencio Núñez Jorge Ulises
*/

package simplecalc;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
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