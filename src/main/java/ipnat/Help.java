/*
 * #%L
 * hIPNAT plugins for Fiji distribution of ImageJ
 * %%
 * Copyright (C) 2016 Tiago Ferreira
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ipnat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.DefaultEditorKit;

import fiji.Debug;
import ij.IJ;
import ij.ImageJ;
import ij.Menus;
import ij.WindowManager;
import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;

/** Implements the "About hIPNAT..." plugin */
public class Help implements PlugIn {

	/** Parameters **/
	private JFrame frame;
	private static String FRAME_TITLE = "About " + IPNAT.ABBREV_NAME;

	/**
	 * Calls {@link fiji.Debug#runPlugIn(String, String, boolean)
	 * fiji.Debug.runPlugIn()} so that the plugin can be debugged from an IDE
	 */
	public static void main(final String[] args) {
		Debug.runPlugIn("ipnat.Help", "", false);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(final String arg) {

		// Check if "About" window is already being displayed
		if (WindowManager.getWindow(FRAME_TITLE) == null) {
			try {
				UIManager.setLookAndFeel(UIManager
						.getSystemLookAndFeelClassName());
			} catch (final Exception ignored) {
			}
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					displayGUI();
				}
			});
		} else {
			IJ.selectWindow(FRAME_TITLE);
		}

	}

	/** Displays the "About..." dialog. */
	void displayGUI() {

		frame = new JFrame(FRAME_TITLE);
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(final WindowEvent we) {
				quit();
			}

			public void windowActivated(final WindowEvent we) {
				if (IJ.isMacintosh() && frame != null) {
					IJ.wait(10);
					frame.setMenuBar(Menus.getMenuBar());
				}
			}
		});

		// Allow frame to be dismissed using the keyboard
		final KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(
				KeyEvent.VK_ESCAPE, 0);
		@SuppressWarnings("serial")
		final Action escapeAction = new AbstractAction() {
			public void actionPerformed(final ActionEvent e) {
				quit();
			}
		};
		frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(escapeKeyStroke, "ESCAPE");
		frame.getRootPane().getActionMap().put("ESCAPE", escapeAction);

		// Pane for HTML contents
		final JEditorPane htmlPane = new JEditorPane();
		htmlPane.setEditable(false);
		htmlPane.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		htmlPane.setContentType("text/html");
		addPopupMenu(htmlPane);

		// Change default font
		try {
			htmlPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,
					true);
			htmlPane.setFont(UIManager.getFont("Label.font"));
		} catch (final Exception ignored) {
		}

		// Add contents
		htmlPane.setText("<HTML>"
				+ "<head>"
				+ "<style type=\"text/css\">"
				+ "h3 {margin-bottom:0; margin-top:15} "
				+ "a {color:#002390; text-decoration:none} "
				+ "</style>"
				+ "</head>"
				+ "<div WIDTH=390>"
				+ "<h3>" + IPNAT.getReadableVersion() + "</h3>"
				+ IPNAT.EXTENDED_NAME
				+ "<h3>Author/Maintainer</h3>"
				+ "<a href='http://imagej.net/User:Tiago'>Tiago Ferreira</a>"
				+ "<h3>Development</h3>"
				+ "<a href='"+ IPNAT.SRC_URL +"/releases'>Release History</a> | "
				+ "<a href='"+ IPNAT.SRC_URL +"'>Source Code</a> | "
				+ "<a href='"+ IPNAT.SRC_URL +"/issues'>Issues</a> | "
				//+ "<a href='"+ IPNAT.API_URL +"'>API</a>"
				+ "<h3>License</h3>"
				+ "<a href='http://opensource.org/licenses/GPL-3.0'>GNU General Public License (GPL)</a>"
				+ "</div></HTML>");

		// Ensure panel is scrolled to the top
		htmlPane.setCaretPosition(0);

		// Make URLs browsable
		htmlPane.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(final HyperlinkEvent e) {
				if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType()))
					try {
						BrowserLauncher.openURL(e.getURL().toString());
					} catch (final IOException ignored) {
					}
			}
		});

		// Panel to hold HTML pane
		final JScrollPane scrollPane = new JScrollPane(htmlPane);
		scrollPane.setPreferredSize(new Dimension(405, 200));
		frame.add(scrollPane, BorderLayout.CENTER);

		// Panel to hold side buttons, all having fixed width
		final JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.PAGE_AXIS));

		addHeaderLabel(buttonPanel, "Documentation Resources:");
		JButton button = URLButton("Neuroanatomy Tools", "http://imagej.net/Neuroanatomy");
		buttonPanel.add(button);
		button = URLButton("Neuroanatomy Update Site", "http://imagej.net/User:Neuroanatomy");
		buttonPanel.add(button);
		button = URLButton(IPNAT.ABBREV_NAME +" on GitHub", IPNAT.SRC_URL);
		buttonPanel.add(button);

		addHeaderLabel(buttonPanel, "ImageJ Resources:");
		button = URLButton("Search Portal", "http://search.imagej.net");
		buttonPanel.add(button);
		button = URLButton("Forum", "http://forum.imagej.net");
		buttonPanel.add(button);

		addHeaderLabel(buttonPanel, "Utilities:");
		button = plainButton("Check for Updates...");
		buttonPanel.add(button);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				quit();
				IJ.doCommand("Update..."); //net.imagej.ui.swing.updater.ImageJUpdater;
			}
		});
		frame.add(buttonPanel, BorderLayout.WEST);

		// Improve cross-platform rendering
		final ImageJ ij = IJ.getInstance();
		if (ij != null && !IJ.isMacOSX()) {
			final Image img = ij.getIconImage();
			if (img != null)
				try {
					frame.setIconImage(img);
				} catch (final Exception ignored) {
				}
		}

		frame.pack();
		frame.setVisible(true);
		WindowManager.addWindow(frame);

	}

	/** Adds a customized heading to the specified panel. */
	void addHeaderLabel(final JPanel p, final String label) {
		final JLabel lbl = new JLabel(label);
		lbl.setForeground(Color.DARK_GRAY);
		lbl.setBorder(new EmptyBorder(10, 3, 0, 0));
		final Font lblFont = lbl.getFont();
		lbl.setFont(lblFont.deriveFont((float) (lblFont.getSize() - 1.5)));
		p.add(lbl);
	}

	/**
	 * Constructs a JButton with the specified label that opens the specified
	 * URL in the user's Browser
	 */
	JButton URLButton(final String label, final String URL) {
		final JButton button = new JButton(label);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button
				.getMinimumSize().height));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				try {
					BrowserLauncher.openURL(URL);
				} catch (final Exception localException) {
					IJ.handleException(localException);
				}
			}
		});
		return button;

	}

	/** Returns a button w/ "maximized" width */
	JButton plainButton(final String label) {
		final JButton button = new JButton(label);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button
				.getMinimumSize().height));
		return button;
	}

	/** Adds a basic popupMenu to the specified pane */
	void addPopupMenu(final JEditorPane pane) {
		final JPopupMenu menu = new JPopupMenu();
		final ActionMap actionMap = pane.getActionMap();

		// Copy to clipboard
		final Action cAction = actionMap.get(DefaultEditorKit.copyAction);
		final JMenuItem cItem = new JMenuItem(cAction);
		cItem.setText("Copy");
		menu.add(cItem);

		// Select all
		final Action sAction = actionMap.get(DefaultEditorKit.selectAllAction);
		final JMenuItem sItem = new JMenuItem(sAction);
		sItem.setText("Select All");
		menu.add(sItem);

		// Invert colors
		final JMenuItem iItem = new JMenuItem("Invert Colors");
		iItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final Color bColor = pane.getBackground();
				final Color fColor = pane.getForeground();
				pane.setBackground(fColor);
				pane.setForeground(bColor);
			}
		});
		menu.addSeparator();
		menu.add(iItem);

		// Disable irrelevant items
		pane.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				enableItems(e);
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				enableItems(e);
			}

			private void enableItems(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					pane.requestFocusInWindow();
					final String selectedText = pane.getSelectedText();
					cItem.setEnabled(selectedText != null
							&& selectedText.length() > 0);
				}

			}
		});

		pane.setComponentPopupMenu(menu);
	}

	/** Disposes and unregisters main frame from WindowManager */
	void quit() {
		frame.dispose();
		WindowManager.removeWindow(frame);
	}

}
