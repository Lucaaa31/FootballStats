package unibo.footstats.view;

import unibo.footstats.controller.Controller;
import unibo.footstats.utility.Context;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CompetitionView extends JPanel {

    private JComboBox<String> comboBox;
    private JButton backButton;
    private JPanel logoPanel;

    public CompetitionView(final Controller controller) {
        setLayout(new BorderLayout());

        // Initialize the combo box
        String[] options = controller.getSeasons().toArray(new String[0]);
        comboBox = new JComboBox<>(options);
        add(comboBox, BorderLayout.NORTH);

        // Initialize the logo panel
        logoPanel = new JPanel();
        logoPanel.setLayout(new GridLayout(2, 4, 10, 10)); // Adjust the grid size based on the number of logos
        add(logoPanel, BorderLayout.CENTER);

        // Load and add the logos
        addLogo("Serie_A");
        addLogo("PL");
        addLogo("Liga");
        addLogo("Ligue1");
        addLogo("Bundesliga");

        // Initialize the back button
        backButton = new JButton("Back");
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.setContext(Context.HOME_PAGE);
                CompetitionView.super.setVisible(false);
            }
        });

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(backButton, BorderLayout.WEST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void addLogo(final String imagePath) {
        ImageIcon icon = new ImageIcon(ClassLoader.getSystemResource("images/competitions/" + imagePath + ".png"));
        icon = new ImageIcon(icon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH));
        JLabel label = new JLabel(icon);
        label.setHorizontalAlignment(JLabel.CENTER);
        logoPanel.add(label);
    }
}