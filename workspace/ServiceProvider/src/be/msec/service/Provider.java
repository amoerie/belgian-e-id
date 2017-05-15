package be.msec.service;

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.net.ssl.SSLServerSocketFactory;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.awt.event.ActionEvent;
import java.awt.GridLayout;
import javax.swing.SwingConstants;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;

public class Provider extends JFrame {

	private JPanel contentPane;
	private JPanel selectionPane;
	
	private JComboBox cbDomain;
	private JComboBox cbService;
	private JButton btnInitButton;
	
	public static JTextArea output;
	public static JTextArea logging;
	

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Provider frame = new Provider();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public Provider() {
		initComponents();
		createEvents();
	}

	private void createEvents() {
		btnInitButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				initServiceProvider();
			}
		});
		
		cbDomain.addItemListener(new ItemListener() {
	        public void itemStateChanged(ItemEvent arg0) {
	        	cbService.removeAllItems();
	        	switch (arg0.getItem().toString()) {
	            case "eGov":  
	            	cbService.addItem("studentportal");
	        	    cbService.addItem("taxonweb");
	                break;
	            case "SocNet":  
	            	cbService.addItem("facebook");
	        	    cbService.addItem("google");
	                break;
	            case "SuperMarket":  
	            	cbService.addItem("colruyt");
	        	    cbService.addItem("delhaize");
	                break;
	            default:
	            	cbService.addItem("kinepolis");
	        	    cbService.addItem("planckendael");
	            	break;
	        	}
	        }
	    });
		
	}

	protected void initServiceProvider() {
		
		String domain = (String)cbDomain.getSelectedItem();
		String service = (String)cbService.getSelectedItem();
		
		logging.setText(logging.getText() + "\nStarting the Service Provider " + service + ""
				+ " of Domain " + domain);
		
//      System.setProperty("javax.net.debug", "ssl");
//		System.setProperty("javax.net.ssl.keyStoreType", "jks");
//		System.setProperty("javax.net.ssl.keyStore", "src/belgianeid.jks");
//		System.setProperty("javax.net.ssl.keyStorePassword", "123456");
//		System.setProperty("javax.net.ssl.trustStoreType", "jks");
//		System.setProperty("javax.net.ssl.trustStore", "src/belgianeid.jks");
//		System.setProperty("javax.net.ssl.trustStorePassword", "123456");
         
        ServerSocket serverSocket = null;
        try {
			//serverSocket = ((SSLServerSocketFactory)SSLServerSocketFactory.getDefault()).createServerSocket(4444);
			serverSocket = new ServerSocket(8888);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
//        while (true)
//			try {
//				Socket clientSocket = serverSocket.accept();
//				new ProviderThread(clientSocket).start();
        		new ProviderThread(serverSocket).start();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
	}

	private void initComponents() {
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		contentPane.add(tabbedPane, BorderLayout.CENTER);
		
		
		//Selection Panel
		selectionPane = new JPanel();
		selectionPane.setLayout(new GridLayout(7, 3, 10, 10));
		tabbedPane.addTab("Selection", selectionPane);
	    
	    //row 1
  		JLabel lblDomain = new JLabel("Domain");
  		lblDomain.setHorizontalAlignment(SwingConstants.RIGHT);
  		selectionPane.add(lblDomain);
	    cbDomain = new JComboBox();
	    cbDomain.addItem("eGov");
	    cbDomain.addItem("SocNet");
	    cbDomain.addItem("SuperMarket");
	    cbDomain.addItem("default");
	    selectionPane.add(cbDomain);
	    
	    //row 2
	    JLabel lblService = new JLabel("Service");
	    lblService.setHorizontalAlignment(SwingConstants.RIGHT);
  		selectionPane.add(lblService);
	    cbService = new JComboBox();
	    cbService.addItem("studentportal");
	    cbService.addItem("taxonweb");
	    selectionPane.add(cbService);
	    
	    //row3
	    JCheckBox chckbxNewCheckBox = new JCheckBox("nym");
	    selectionPane.add(chckbxNewCheckBox);
	    JCheckBox chckbxName = new JCheckBox("name");
	    selectionPane.add(chckbxName);
	    
	    //row4
	    JCheckBox chckbxAddress = new JCheckBox("address");
	    selectionPane.add(chckbxAddress);
	    JCheckBox chckbxCountry = new JCheckBox("country");
	    selectionPane.add(chckbxCountry);
	    
	    //row5
	    JCheckBox chckbxBirthdate = new JCheckBox("birthdate");
	    selectionPane.add(chckbxBirthdate);
	    JCheckBox chckbxAge = new JCheckBox("age");
	    selectionPane.add(chckbxAge);
	    
	    //row6
	    JCheckBox chckbxGender = new JCheckBox("gender");
	    selectionPane.add(chckbxGender);
	    JCheckBox chckbxPicture = new JCheckBox("picture");
	    selectionPane.add(chckbxPicture);
	    
	    //Output Panel
  		JPanel outputPanel = new JPanel();
  		output = new JTextArea();
  		output.setColumns(50);
  		output.setRows(10);
  		outputPanel.add(output);
  	    tabbedPane.addTab("Output", outputPanel);
  	    
  	    //Logging Panel
  		JPanel loggingPanel = new JPanel();
  		logging = new JTextArea();
  		logging.setColumns(50);
  		logging.setRows(10);
  		loggingPanel.add(logging);
  	    tabbedPane.addTab("Logging", loggingPanel);
  	    
  	    btnInitButton = new JButton("Init provider");
  	    contentPane.add(btnInitButton, BorderLayout.SOUTH);
  	    
  	    logging.setText(logging.getText() + "Init components is done...");
		
	}

}
