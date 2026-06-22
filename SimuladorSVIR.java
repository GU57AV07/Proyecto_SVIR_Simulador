import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class SimuladorSVIR extends JFrame {
    private JTextField txtBeta, txtGamma, txtNu, txtN, txtI0;
    private JComboBox<String> comboVista; // El nuevo menú selector de curvas
    private JButton btnSimular, btnExportar;
    private JPanel panelGrafica;
    
    private ArrayList<Double> histS, histV, histI, histR;

    // --- PALETA DE COLORES MODERNA ---
    private final Color COLOR_BG = new Color(241, 245, 249);       
    private final Color COLOR_SIDEBAR = new Color(255, 255, 255);  
    private final Color COLOR_TEXT = new Color(15, 23, 42);        
    
    private final Color BTN_PRIMARY = new Color(79, 70, 229);      
    private final Color BTN_ACCENT = new Color(5, 150, 105);       

    private final Color CURVA_S = new Color(37, 99, 235);   // Azul
    private final Color CURVA_V = new Color(22, 163, 74);   // Verde
    private final Color CURVA_I = new Color(220, 38, 38);   // Rojo
    private final Color CURVA_R = new Color(147, 51, 234);  // Morado

    private final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 13);
    private final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);

    public SimuladorSVIR() {
        setTitle("Simulador Epidemiológico SVIR");
        setSize(1050, 700); // Incrementado ligeramente el alto para acomodar el menú
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(COLOR_BG);

        // --- PANEL DE PARÁMETROS (IZQUIERDA) ---
        JPanel panelIzquierdo = new JPanel(new BorderLayout(10, 10));
        panelIzquierdo.setBackground(COLOR_SIDEBAR);
        panelIzquierdo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(226, 232, 240)),
                new EmptyBorder(25, 20, 25, 20)
        ));
        panelIzquierdo.setPreferredSize(new Dimension(310, 600));

        // Aumentamos a 6 filas en el Grid para el nuevo menú desplegable
        JPanel panelInputs = new JPanel(new GridLayout(6, 2, 10, 15));
        panelInputs.setBackground(COLOR_SIDEBAR);

        txtBeta = crearTextField("0.85");
        txtGamma = crearTextField("0.25");
        txtNu = crearTextField("0.05");
        txtN = crearTextField("100000");
        txtI0 = crearTextField("90");

        agregarInput(panelInputs, "β (Contagio):", txtBeta);
        agregarInput(panelInputs, "γ (Recuperación):", txtGamma);
        agregarInput(panelInputs, "ν (Vacunación):", txtNu);
        agregarInput(panelInputs, "Población (N):", txtN);
        agregarInput(panelInputs, "Infectados (I0):", txtI0);

        // Configuración del JComboBox (Menú de Selección de Vista)
        JLabel lblVista = new JLabel("Filtro de Vista:");
        lblVista.setFont(FONT_BOLD);
        lblVista.setForeground(COLOR_TEXT);
        panelInputs.add(lblVista);

        comboVista = new JComboBox<>(new String[]{
            "General",
            "Susceptibles (S)",
            "Vacunados (V)",
            "Infectados (I)",
            "Recuperados (R)"
        });
        comboVista.setFont(FONT_MAIN);
        comboVista.setBackground(Color.WHITE);
        // Escucha cambios en el menú para refrescar la gráfica al instante
        comboVista.addActionListener(e -> panelGrafica.repaint());
        panelInputs.add(comboVista);

        JPanel panelBotones = new JPanel(new GridLayout(2, 1, 0, 12));
        panelBotones.setBackground(COLOR_SIDEBAR);

        btnSimular = new JButton("Simular Evolución");
        estilarBotones(btnSimular, BTN_PRIMARY);
        btnSimular.addActionListener(e -> simular());

        btnExportar = new JButton("Descargar Resumen (.txt)");
        estilarBotones(btnExportar, BTN_ACCENT);
        btnExportar.setEnabled(false); 
        btnExportar.addActionListener(e -> exportarResultados());

        panelBotones.add(btnSimular);
        panelBotones.add(btnExportar);

        panelIzquierdo.add(panelInputs, BorderLayout.CENTER);
        panelIzquierdo.add(panelBotones, BorderLayout.SOUTH);
        add(panelIzquierdo, BorderLayout.WEST);

        // --- PANEL DE LA GRÁFICA MULTILÍNEA (CENTRO) ---
        panelGrafica = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int padding = 60;

                g2.setColor(Color.WHITE);
                g2.fillRect(padding, padding, w - padding * 2, h - padding * 2);

                // Cuadrícula de fondo
                g2.setColor(new Color(241, 245, 249));
                g2.setStroke(new BasicStroke(1.0f));
                for (int i = 1; i <= 10; i++) {
                    int yGrid = h - padding - (i * (h - padding * 2) / 10);
                    g2.drawLine(padding, yGrid, w - padding, yGrid);
                    int xGrid = padding + (i * (w - padding * 2) / 10);
                    g2.drawLine(xGrid, padding, xGrid, h - padding);
                }

                // Ejes
                g2.setColor(new Color(148, 163, 184));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(padding, h - padding, w - padding, h - padding); 
                g2.drawLine(padding, padding, padding, h - padding);         

                g2.setFont(FONT_MAIN);
                g2.setColor(COLOR_TEXT);
                g2.drawString("Tiempo (Días del 0 al 120)", w / 2 - 60, h - 20);
                g2.drawString("% de la Población Total", 15, padding - 20);

                // --- LÓGICA DE VISUALIZACIÓN FILTRADA ---
                int seleccion = comboVista.getSelectedIndex();
                g2.setStroke(new BasicStroke(2.8f)); 

                // Dependiendo de la opción del JComboBox, dibujamos las curvas correspondientes
                if (seleccion == 0 || seleccion == 1) dibujarLineaEvolucion(g2, histS, CURVA_S, padding, w, h);
                if (seleccion == 0 || seleccion == 2) dibujarLineaEvolucion(g2, histV, CURVA_V, padding, w, h);
                if (seleccion == 0 || seleccion == 3) dibujarLineaEvolucion(g2, histI, CURVA_I, padding, w, h);
                if (seleccion == 0 || seleccion == 4) dibujarLineaEvolucion(g2, histR, CURVA_R, padding, w, h);

                // --- RECUADRO DE LEYENDA DINÁMICA ---
                int lx = w - padding - 170;
                int ly = padding + 20;
                
                if (seleccion == 0) {
                    // Si se ven todas, dibujamos el cuadro completo de leyenda
                    g2.setColor(new Color(255, 255, 255, 230)); 
                    g2.fillRect(lx - 10, ly - 10, 165, 95);
                    g2.setColor(new Color(203, 213, 225));
                    g2.drawRect(lx - 10, ly - 10, 165, 95);

                    Color[] coloresLeyenda = {CURVA_S, CURVA_V, CURVA_I, CURVA_R};
                    String[] textosLeyenda = {"Susceptibles (S)", "Vacunados (V)", "Infectados (I)", "Recuperados (R)"};

                    for (int i = 0; i < textosLeyenda.length; i++) {
                        g2.setColor(coloresLeyenda[i]);
                        g2.fillRect(lx, ly + (i * 20), 12, 12);
                        g2.setColor(COLOR_TEXT);
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                        g2.drawString(textosLeyenda[i], lx + 20, ly + (i * 20) + 11);
                    }
                } else {
                    // Si sólo se ve una curva, mostramos únicamente su indicador para limpiar la pantalla
                    g2.setColor(new Color(255, 255, 255, 230)); 
                    g2.fillRect(lx - 10, ly - 10, 165, 35);
                    g2.setColor(new Color(203, 213, 225));
                    g2.drawRect(lx - 10, ly - 10, 165, 35);

                    Color colActual = seleccion == 1 ? CURVA_S : seleccion == 2 ? CURVA_V : seleccion == 3 ? CURVA_I : CURVA_R;
                    String txtActual = comboVista.getSelectedItem().toString();

                    g2.setColor(colActual);
                    g2.fillRect(lx, ly, 12, 12);
                    g2.setColor(COLOR_TEXT);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    g2.drawString(txtActual, lx + 20, ly + 11);
                }
            }
        };
        
        panelGrafica.setBackground(COLOR_BG);
        TitledBorder borderGrafica = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                " Curvas de Evolución Temporal SVIR "
        );
        borderGrafica.setTitleFont(FONT_BOLD);
        borderGrafica.setTitleColor(BTN_PRIMARY);
        panelGrafica.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 10, 10, 10),
                borderGrafica
        ));
        
        add(panelGrafica, BorderLayout.CENTER);
    }

    private void dibujarLineaEvolucion(Graphics2D g2, ArrayList<Double> datos, Color color, int padding, int w, int h) {
        if (datos == null || datos.isEmpty()) return;
        g2.setColor(color);
        for (int i = 1; i < datos.size(); i++) {
            int x1 = padding + (int) ((i - 1) * (w - padding * 2) / (datos.size() - 1));
            int y1 = h - padding - (int) (datos.get(i - 1) * (h - padding * 2) / 100.0);
            int x2 = padding + (int) (i * (w - padding * 2) / (datos.size() - 1));
            int y2 = h - padding - (int) (datos.get(i) * (h - padding * 2) / 100.0);
            g2.drawLine(x1, y1, x2, y2);
        }
    }

    private JTextField crearTextField(String defecto) {
        JTextField tf = new JTextField(defecto);
        tf.setFont(FONT_MAIN);
        tf.setForeground(COLOR_TEXT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        return tf;
    }

    private void agregarInput(JPanel panel, String etiqueta, JTextField tf) {
        JLabel lbl = new JLabel(etiqueta);
        lbl.setFont(FONT_BOLD);
        lbl.setForeground(COLOR_TEXT);
        panel.add(lbl);
        panel.add(tf);
    }

    private void estilarBotones(JButton btn, Color colorFondo) {
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(colorFondo);
        btn.setOpaque(true);
        btn.setBorderPainted(false); 
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void simular() {
        try {
            double beta = Double.parseDouble(txtBeta.getText());
            double gamma = Double.parseDouble(txtGamma.getText());
            double nu = Double.parseDouble(txtNu.getText());
            double N = Double.parseDouble(txtN.getText());
            double I0 = Double.parseDouble(txtI0.getText());

            if (I0 > N) {
                JOptionPane.showMessageDialog(this, "La cantidad inicial de infectados no puede ser mayor que N.", "Error numérico", JOptionPane.ERROR_MESSAGE);
                return;
            }

            histS = new ArrayList<>();
            histV = new ArrayList<>();
            histI = new ArrayList<>();
            histR = new ArrayList<>();

            double S = N - I0, V = 0, I = I0, R = 0;

            for (int t = 0; t <= 120; t++) {
                histS.add((S / N) * 100);
                histV.add((V / N) * 100);
                histI.add((I / N) * 100);
                histR.add((R / N) * 100);

                double dS = -beta * S * I / N - nu * S;
                double dV = nu * S;
                double dI = beta * S * I / N - gamma * I;
                double dR = gamma * I;

                S += dS;
                V += dV;
                I += dI;
                R += dR;

                if (S < 0) S = 0; if (V < 0) V = 0; if (I < 0) I = 0; if (R < 0) R = 0;
            }

            btnExportar.setEnabled(true);
            panelGrafica.repaint();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Verifica que todos los parámetros contengan números válidos.", "Error de formato", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarResultados() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Exportar Reporte de Simulación SVIR");
        fileChooser.setSelectedFile(new File("Resumen_Simulacion_SVIR.txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(archivo))) {
                writer.println("=======================================================================");
                writer.println("            REPORTE ESTADÍSTICO DE EVOLUCIÓN EPIDÉMICA (SVIR)          ");
                writer.println("=======================================================================");
                writer.println();
                writer.println("--- CONFIGURACIÓN DE PARÁMETROS ---");
                writer.println("Población Total (N): " + txtN.getText());
                writer.println("Infectados Iniciales (I0): " + txtI0.getText());
                writer.println("Tasa de Contagio (beta): " + txtBeta.getText());
                writer.println("Tasa de Recuperación (gamma): " + txtGamma.getText());
                writer.println("Tasa de Vacunación (nu): " + txtNu.getText());
                writer.println();

                double maxI = 0; int diaPico = 0;
                for (int i = 0; i < histI.size(); i++) {
                    if (histI.get(i) > maxI) {
                        maxI = histI.get(i);
                        diaPico = i;
                    }
                }

                writer.println("--- ANÁLISIS DE MÉTRICAS CRÍTICAS ---");
                writer.printf("Pico máximo de brote infeccioso: %.2f%% de la población.\n", maxI);
                writer.println("Momento del pico máximo: Día " + diaPico);
                writer.printf("Proporciones finales (Día 120): S: %.2f%% | V: %.2f%% | I: %.2f%% | R: %.2f%%\n",
                        histS.get(histS.size()-1), histV.get(histV.size()-1), histI.get(histI.size()-1), histR.get(histR.size()-1));
                writer.println();

                writer.println("--- DETALLE HISTÓRICO COMPLETO (PORCENTAJES) ---");
                writer.printf("%-8s %-18s %-18s %-18s %-18s\n", "Día", "Susceptibles (%)", "Vacunados (%)", "Infectados (%)", "Recuperados (%)");
                writer.println("-----------------------------------------------------------------------");

                for (int t = 0; t < histI.size(); t++) {
                    writer.printf("%-8d %-18.2f %-18.2f %-18.2f %-18.2f\n",
                            t, histS.get(t), histV.get(t), histI.get(t), histR.get(t));
                }

                writer.println("=======================================================================");
                writer.flush();
                
                JOptionPane.showMessageDialog(this, "Reporte descargado correctamente en:\n" + archivo.getAbsolutePath(), 
                        "Descarga Finalizada", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error de escritura de E/S al guardar el archivo de texto.", 
                        "Error de Guardado", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimuladorSVIR().setVisible(true));
    }
}