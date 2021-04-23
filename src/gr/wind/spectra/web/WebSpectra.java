package gr.wind.spectra.web;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebParam.Mode;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gr.wind.spectra.business.DB_Connection;
import gr.wind.spectra.business.DB_Operations;
import gr.wind.spectra.business.Help_Func;
import gr.wind.spectra.business.IncidentOutageToCSV;
import gr.wind.spectra.business.OpenningIncidentOutageToCSV;
import gr.wind.spectra.business.Test_CLIOutage;
import gr.wind.spectra.business.s_DB_Connection;
import gr.wind.spectra.business.s_DB_Operations;
import gr.wind.spectra.consumerRequests.Async_CloseOutage;
import gr.wind.spectra.consumerRequests.Async_GetHierarchy;
import gr.wind.spectra.consumerRequests.Async_ModifyOutage;
import gr.wind.spectra.consumerRequests.Async_NLUActive;
import gr.wind.spectra.consumerRequests.Async_SubmitOutage;
import gr.wind.spectra.model.ProductOfCloseOutage;
import gr.wind.spectra.model.ProductOfGetHierarchy;
import gr.wind.spectra.model.ProductOfGetOutage;
import gr.wind.spectra.model.ProductOfModify;
import gr.wind.spectra.model.ProductOfNLUActive;
import gr.wind.spectra.model.ProductOfSubmission;

@WebService(endpointInterface = "gr.wind.spectra.web.InterfaceWebSpectra")
public class WebSpectra implements InterfaceWebSpectra
{
	// Those directive is for IP retrieval of web request :-)
	@Resource
	private WebServiceContext wsContext;

	public WebSpectra()
	{

	}

	@Override
	@WebMethod()
	@WebResult(name = "Result")
	public List<ProductOfGetHierarchy> getHierarchy(
			@WebParam(name = "UserName", header = true, mode = Mode.IN) String UserName,
			@WebParam(name = "Password", header = true, mode = Mode.IN) String Password,
			@WebParam(name = "RequestID") @XmlElement(required = true) String RequestID,
			@WebParam(name = "RequestTimestamp") @XmlElement(required = true) String RequestTimestamp,
			@WebParam(name = "SystemID") @XmlElement(required = true) String SystemID,
			@WebParam(name = "UserID") @XmlElement(required = true) String UserID,
			@WebParam(name = "Hierarchy") String Hierarchy) throws Exception, InvalidInputException
	{

		Logger logger = LogManager.getLogger(gr.wind.spectra.web.WebSpectra.class.getName());
		String hierSep = "->";
		Connection conn = null;
		DB_Connection conObj = null;
		DB_Operations dbs = null;
		Connection s_conn = null;
		s_DB_Connection s_conObj = null;
		s_DB_Operations s_dbs = null;
		MessageContext mc = null;
		HttpServletRequest req = null;
		//System.out.println("Client IP = " + req.getRemoteAddr());

		if (conn == null)
		{
			try
			{
				conObj = new DB_Connection();
				conn = conObj.connect();
				dbs = new DB_Operations(conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		if (s_conn == null)
		{
			try
			{
				s_conObj = new s_DB_Connection();
				s_conn = s_conObj.connect();
				s_dbs = new s_DB_Operations(s_conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		/*
		 * <DataCustomersAffected>34</potentialCustomersAffected>// unique user names
		 * from Data Resource path
		 * <voiceCustomersAffected>34</potentialCustomersAffected> // unique user names
		 * from Voice Resource path <cLIsAffected>34</potentialCustomersAffected> //
		 * unique CLIs from Voice Resource path
		 */

		new WebSpectra();
		try
		{
			Help_Func hf = new Help_Func();

			// Those 2 directives is for IP retrieval of web request
			mc = wsContext.getMessageContext();
			req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

			//establishDBConnection();
			//establishStaticTablesDBConnection();

			logger.trace(
					req.getRemoteAddr() + " - ReqID: " + RequestID + " - Get Hierarchy: Establishing DB Connection");
			List<String> ElementsList = new ArrayList<String>();
			List<ProductOfGetHierarchy> prodElementsList = new ArrayList<>();

			// Check if Authentication credentials are correct.
			if (!s_dbs.authenticateRequest(UserName, Password, "test_remedyService"))
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ "Get Hierarchy: - Wrong credentials provided - UserName: " + UserName + " Password: "
						+ Password);
				throw new InvalidInputException("User name or Password incorrect!", "Error 100");
			}

			// Check if Required fields are empty
			hf.validateNotEmpty("RequestID", RequestID);
			hf.validateNotEmpty("SystemID", SystemID);
			hf.validateNotEmpty("UserID", UserID);

			// Validate Date Formats if the fields are not empty
			if (!hf.checkIfEmpty("RequestTimestamp", RequestTimestamp))
			{
				hf.validateDateTimeFormat("RequestTimestamp", RequestTimestamp);
			}

			// Update Statistics
			s_dbs.updateUsageStatisticsForMethod("GetHierarchy");

			// No Hierarchy is given - returns root elements
			if (Hierarchy == null || Hierarchy.equals("") || Hierarchy.equals("?"))
			{
				logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ " - Get Hierarchy: Hierarchy Requested: <empty>");

				ElementsList = dbs.getOneColumnUniqueResultSet("HierarchyTablePerTechnology2", "RootHierarchyNode",
						new String[] {}, new String[] {}, new String[] {});

				String[] nodeNames = new String[] {};
				String[] nodeValues = new String[] {};
				ProductOfGetHierarchy pr = new ProductOfGetHierarchy(dbs, new String[] {}, new String[] {},
						new String[] {}, Hierarchy, "rootElements", ElementsList, nodeNames, nodeValues, RequestID,
						"No");
				prodElementsList.add(pr);
			} else
			{
				logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Get Hierarchy: Hierarchy Requested: "
						+ Hierarchy);
				ArrayList<String> nodeNamesArrayList = new ArrayList<String>();
				ArrayList<String> nodeValuesArrayList = new ArrayList<String>();

				// Get root hierarchy String
				String rootElementInHierarchy = hf.getRootHierarchyNode(Hierarchy);

				// Get Hierarchy Table for that root hierarchy
				String table = dbs.getOneValue("HierarchyTablePerTechnology2", "HierarchyTableName",
						new String[] { "RootHierarchyNode" }, new String[] { rootElementInHierarchy },
						new String[] { "String" });

				// Get Hierarchy data in style :
				// OltElementName->OltSlot->OltPort->Onu->ElementName->Slot
				String fullHierarchyFromDB = dbs.getOneValue("HierarchyTablePerTechnology2", "HierarchyTableNamePath",
						new String[] { "RootHierarchyNode" }, new String[] { rootElementInHierarchy },
						new String[] { "String" });

				// Check Columns of Hierarchy against fullHierarchy (avoid wrong key values in
				// hierarchy e.g. SiteNa7me=AKADIMIAS)
				hf.checkColumnsOfHierarchyVSFullHierarchy(Hierarchy, fullHierarchyFromDB);

				// Split the hierarchy retrieved from DB into fields
				String[] fullHierarchyFromDBSplit = fullHierarchyFromDB.split("->");

				// Get Full Data hierarchy in style :
				// OltElementName->OltSlot->OltPort->Onu->ActiveElement->Slot
				String fullDataSubsHierarchyFromDB = dbs.getOneValue("HierarchyTablePerTechnology2",
						"DataSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
						new String[] { rootElementInHierarchy }, new String[] { "String" });

				// Split the Data hierarchy retrieved from DB into fields
				String[] fullDataSubsHierarchyFromDBSplit = fullDataSubsHierarchyFromDB.split("->");

				// Get Full Voice hierarchy in style :
				// OltElementName->OltSlot->OltPort->Onu->ActiveElement->Slot
				String fullVoiceSubsHierarchyFromDB = dbs.getOneValue("HierarchyTablePerTechnology2",
						"VoiceSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
						new String[] { rootElementInHierarchy }, new String[] { "String" });

				// Split the Data hierarchy retrieved from DB into fields
				String[] fullVoiceSubsHierarchyFromDBSplit = fullVoiceSubsHierarchyFromDB.split("->");

				// Split given hierarchy
				String[] hierItemsGiven = Hierarchy.split(hierSep);

				// Check if max hierarchy level is surpassed
				// Max hierarchy level is fullHieararchyPath.length + 1
				int maxLevelsOfHierarchy = fullHierarchyFromDBSplit.length + 1;
				if (hierItemsGiven.length > maxLevelsOfHierarchy)
				{
					throw new InvalidInputException("More hierarchy levels than expected", "Error 120");
				}

				// If only root Hierarchy is given
				if (hierItemsGiven.length == 1)
				{
					// ElementsList = dbs.GetOneColumnUniqueResultSet(table,
					// fullHierarchyFromDBSplit[0], " 1 = 1 ");

					ElementsList = dbs.getOneColumnUniqueResultSet(table, fullHierarchyFromDBSplit[0], new String[] {},
							new String[] {}, new String[] {});

					String[] nodeNames = new String[] { rootElementInHierarchy };
					String[] nodeValues = new String[] { "1" };

					ProductOfGetHierarchy pr = new ProductOfGetHierarchy(dbs, fullHierarchyFromDBSplit,
							fullDataSubsHierarchyFromDBSplit, fullVoiceSubsHierarchyFromDBSplit, Hierarchy,
							fullHierarchyFromDBSplit[0], ElementsList, nodeNames, nodeValues, RequestID,
							dbs.determineWSAffected(Hierarchy));
					prodElementsList.add(pr);
				} else
				{
					// Check if Max hierarchy is used
					// FTTX->OltElementName=LAROAKDMOLT01->OltSlot=1->OltPort=0->Onu=0->ElementName=LAROAKDMOFLND010H11->Slot=4:
					// 7 MAX = FTTX + OltElementName->OltSlot->OltPort->Onu->ElementName->Slot
					if (hierItemsGiven.length < fullHierarchyFromDBSplit.length + 1)
					{
						// If a full hierarchy is given
						for (int i = 0; i < hierItemsGiven.length; i++)
						{
							if (i == 0)
							{
								nodeNamesArrayList.add(rootElementInHierarchy);
								nodeValuesArrayList.add("1");
								continue;
							}

							String[] keyValue = hierItemsGiven[i].split("=");
							nodeNamesArrayList.add(keyValue[0]);
							nodeValuesArrayList.add(keyValue[1]);
						}

						// ElementsList = dbs.GetOneColumnUniqueResultSet(table,
						// fullHierarchyFromDBSplit[hierItemsGiven.length - 1],
						// Help_Func.HierarchyToPredicate(Hierarchy));

						ElementsList = dbs.getOneColumnUniqueResultSet(table,
								fullHierarchyFromDBSplit[hierItemsGiven.length - 1], hf.hierarchyKeys(Hierarchy),
								hf.hierarchyValues(Hierarchy), hf.hierarchyStringTypes(Hierarchy));

						String[] nodeNames = nodeNamesArrayList.toArray(new String[nodeNamesArrayList.size()]);
						String[] nodeValues = nodeValuesArrayList.toArray(new String[nodeValuesArrayList.size()]);
						ProductOfGetHierarchy pr = new ProductOfGetHierarchy(dbs, fullHierarchyFromDBSplit,
								fullDataSubsHierarchyFromDBSplit, fullVoiceSubsHierarchyFromDBSplit, Hierarchy,
								fullHierarchyFromDBSplit[hierItemsGiven.length - 1], ElementsList, nodeNames,
								nodeValues, RequestID, dbs.determineWSAffected(Hierarchy));
						prodElementsList.add(pr);
					} else
					{ // Max Hierarchy Level
						// If a full hierarchy is given
						for (int i = 0; i < hierItemsGiven.length; i++)
						{
							if (i == 0)
							{
								nodeNamesArrayList.add(rootElementInHierarchy);
								nodeValuesArrayList.add("1");
								continue;
							}

							String[] keyValue = hierItemsGiven[i].split("=");
							nodeNamesArrayList.add(keyValue[0]);
							nodeValuesArrayList.add(keyValue[1]);
						}

						ElementsList = new ArrayList<String>();
						String[] nodeNames = nodeNamesArrayList.toArray(new String[nodeNamesArrayList.size()]);
						String[] nodeValues = nodeValuesArrayList.toArray(new String[nodeValuesArrayList.size()]);
						ProductOfGetHierarchy pr = new ProductOfGetHierarchy(dbs, fullHierarchyFromDBSplit,
								fullDataSubsHierarchyFromDBSplit, fullVoiceSubsHierarchyFromDBSplit, Hierarchy,
								"MaxLevel", ElementsList, nodeNames, nodeValues, RequestID,
								dbs.determineWSAffected(Hierarchy));
						prodElementsList.add(pr);
					}
				}
			}

			return prodElementsList;
		} catch (Exception e)
		{
			throw e;
		} finally
		{
			// Send Similar request to Spectra_Reporting server
			try
			{
				Async_GetHierarchy aGH = new Async_GetHierarchy(UserName, Password, RequestID, RequestTimestamp,
						SystemID, UserID, Hierarchy);
				aGH.run();
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try
			{
				logger.trace(
						req.getRemoteAddr() + " - ReqID: " + RequestID + " - Get Hierarchy: Closing DB Connection");
				if (conObj != null)
				{
					conObj.closeDBConnection();
				}
				if (s_conObj != null)
				{
					s_conObj.closeDBConnection();
				}
				conn = null;
				conObj = null;
				dbs = null;
				s_conn = null;
				s_conObj = null;
				s_dbs = null;
				mc = null;
				req = null;

			} catch (Exception e)
			{
				logger.info("Get Hierarchy Finally block");
			}
		}

	}

	@Override
	@WebMethod
	@WebResult(name = "Result")
	public List<ProductOfSubmission> submitOutage(
			@WebParam(name = "UserName", header = true, mode = Mode.IN) String UserName,
			@WebParam(name = "Password", header = true, mode = Mode.IN) String Password,
			@WebParam(name = "RequestID") @XmlElement(required = true) String RequestID,
			@WebParam(name = "RequestTimestamp") @XmlElement(required = true) String RequestTimestamp,
			@WebParam(name = "SystemID") @XmlElement(required = true) String SystemID,
			@WebParam(name = "UserID") @XmlElement(required = true) String UserID,
			@WebParam(name = "IncidentID") @XmlElement(required = true) String IncidentID,
			@WebParam(name = "Scheduled") @XmlElement(required = true) String Scheduled,
			@WebParam(name = "StartTime") @XmlElement(required = true) String StartTime,
			@WebParam(name = "EndTime") @XmlElement(required = false) String EndTime,
			@WebParam(name = "Duration") @XmlElement(required = false) String Duration,
			// TV, VOICE, DATA
			@WebParam(name = "AffectedServices") @XmlElement(required = true) String AffectedServices,
			// Quality, Loss
			@WebParam(name = "Impact") @XmlElement(required = true) String Impact,
			@WebParam(name = "Priority") @XmlElement(required = true) String Priority,
			@WebParam(name = "HierarchySelected") @XmlElement(required = true) String HierarchySelected)
			throws Exception, InvalidInputException
	{
		Logger logger = LogManager.getLogger(gr.wind.spectra.web.WebSpectra.class.getName());
		Connection conn = null;
		DB_Connection conObj = null;
		DB_Operations dbs = null;
		Connection s_conn = null;
		s_DB_Connection s_conObj = null;
		s_DB_Operations s_dbs = null;
		MessageContext mc = null;
		HttpServletRequest req = null;
		//System.out.println("Client IP = " + req.getRemoteAddr());
		//System.out.println("Client IP = " + req.getRemoteAddr());

		if (conn == null)
		{
			try
			{
				conObj = new DB_Connection();
				conn = conObj.connect();
				dbs = new DB_Operations(conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		if (s_conn == null)
		{
			try
			{
				s_conObj = new s_DB_Connection();
				s_conn = s_conObj.connect();
				s_dbs = new s_DB_Operations(s_conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		new WebSpectra();

		// The below variabes are used for location determination - on a per Incident basis
		String locationsAffected = null;
		ArrayList<String> locationsAffectedList = new ArrayList<>();
		Set<String> uniqueLocationsSet = null;

		try
		{
			Help_Func hf = new Help_Func();

			// Those 2 directives is for IP retrieval of web request
			mc = wsContext.getMessageContext();
			req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

			//establishDBConnection();
			//establishStaticTablesDBConnection();

			logger.trace(
					req.getRemoteAddr() + " - ReqID: " + RequestID + " - Submit Outage: Establishing DB Connection");
			List<ProductOfSubmission> prodElementsList;

			prodElementsList = new ArrayList<>();
			int OutageID_Integer = 0;
			// Check if Authentication credentials are correct.
			if (!s_dbs.authenticateRequest(UserName, Password, "test_remedyService"))
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ "Submit Outage: - Wrong credentials provided - UserName: " + UserName + " Password: "
						+ Password);
				throw new InvalidInputException("User name or Password incorrect!", "Error 100");
			}

			// Check if Required fields are not empty and they contain the desired values
			hf.validateNotEmpty("RequestID", RequestID);
			hf.validateNotEmpty("RequestTimestamp", RequestTimestamp);
			if (!hf.checkIfEmpty("RequestTimestamp", RequestTimestamp))
			{
				hf.validateDateTimeFormat("RequestTimestamp", RequestTimestamp);
			}

			hf.validateNotEmpty("StartTime", StartTime);
			if (!hf.checkIfEmpty("StartTime", StartTime))
			{
				hf.validateDateTimeFormat("StartTime", StartTime);
			}
			if (!hf.checkIfEmpty("EndTime", EndTime))
			{
				hf.validateDateTimeFormat("EndTime", EndTime);
			}

			hf.validateNotEmpty("SystemID", SystemID);
			hf.validateNotEmpty("UserID", UserID);
			hf.validateNotEmpty("IncidentID", IncidentID);

			hf.validateNotEmpty("Scheduled", Scheduled);
			hf.validateAgainstPredefinedValues("Scheduled", Scheduled, new String[] { "Yes", "No" });

			// If the submitted incident is scheduled then it should always has "EndTime"
			if (Scheduled.equals("Yes"))
			{
				if (hf.checkIfEmpty("EndTime", EndTime))
				{
					throw new InvalidInputException("Scheduled incidents should always contain Start Time and End Time",
							"Error 172");
				}
			} else if (Scheduled.equals("No")) // If the submitted incident is NON scheduled then it should NOT contain "EndTime"
			{
				if (!hf.checkIfEmpty("EndTime", EndTime))
				{
					throw new InvalidInputException(
							"Non scheduled incidents should not contain End Time during submission", "Error 173");
				}
			}

			hf.validateIntegerOrEmptyValue("Duration", Duration);

			hf.validateNotEmpty("AffectedServices", AffectedServices);
			hf.validateDelimitedValues("AffectedServices", AffectedServices, "\\|",
					new String[] { "Voice", "Data", "IPTV" });

			hf.validateNotEmpty("Impact", Impact);
			hf.validateAgainstPredefinedValues("Impact", Impact, new String[] { "QoS", "LoS" });

			hf.validateNotEmpty("Priority", Priority);
			hf.validateAgainstPredefinedValues("Priority", Priority,
					new String[] { "Critical", "Medium", "Low", "High" });

			hf.validateNotEmpty("HierarchySelected", HierarchySelected);

			// Split to % and to | the hierarchy provided
			List<String> myHier = hf.getHierarchySelections(HierarchySelected);

			// Get Max Outage ID (type int)
			OutageID_Integer = s_dbs.getMaxIntegerValue("Test_SubmittedIncidents", "OutageID");

			// Services affected
			String[] servicesAffected = AffectedServices.split("\\|");

			/**
			 * 	�	Exception 1 : RootHierarchyNode = Wind_FTTX , HierarchyTableNamePath : 'OltElementName->OltSlot->OltPort->Onu
			 *	�	Exception 2 : RootHierarchyNode = FTTC_Location_Element , HierarchyTableNamePath : Site Name
			 */
			hf.declineSubmissionOnCertainHierarchyLevels(myHier);

			// Update Statistics
			s_dbs.updateUsageStatisticsForMethod("SubmitOutage");

			// Calculate Total number per Indicent, of customers affected per incident
			int incidentDataCustomersAffected = 0;
			int incidentVoiceCustomersAffected = 0;
			int incidentIPTVCustomersAffected = 0;
			for (String service : servicesAffected)
			{
				for (int i = 0; i < myHier.size(); i++)
				{
					// If the sumbission contains only root hierarchy then STOP submission
					if (!myHier.get(i).contains("="))
					{
						throw new InvalidInputException("Cannot submit Incident for an invalid/root only hierarchy",
								"Error 900");
					}

					// Check Hierarchy Format Key_Value Pairs
					hf.checkHierarchyFormatKeyValuePairs(myHier.get(i).toString());

					// Firstly determine the hierarchy table that will be used based on the root
					// hierarchy provided
					String rootHierarchySelected = hf.getRootHierarchyNode(myHier.get(i).toString());

					// Get Hierarchy data in style :
					// OltElementName->OltSlot->OltPort->Onu->ElementName->Slot
					String fullHierarchyFromDB = dbs.getOneValue("HierarchyTablePerTechnology2",
							"HierarchyTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					// Check Columns of Hierarchy against fullHierarchy (avoid wrong key values in
					// hierarchy e.g. SiteNa7me=AKADIMIAS)
					hf.checkColumnsOfHierarchyVSFullHierarchy(myHier.get(i).toString(), fullHierarchyFromDB);

					// Determine Tables for Data/Voice subscribers
					String dataSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "DataSubscribersTableName",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					String IPTVSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "IPTVSubscribersTableName",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					String voiceSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "VoiceSubscribersTableName",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					// Get Hierarchies for Data/Voice Tables
					String fullDataHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
							"DataSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					String[] fullDataHierarchyPathSplit = fullDataHierarchyPath.split("->");

					String fullIPTVHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
							"IPTVSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					String[] fullIPTVHierarchyPathSplit = fullIPTVHierarchyPath.split("->");

					String fullVoiceHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
							"VoiceSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					String[] fullVoiceHierarchyPathSplit = fullVoiceHierarchyPath.split("->");

					// Secondly determine NGA_TYPE based on rootElement
					String ngaTypes = dbs.getOneValue("HierarchyTablePerTechnology2", "NGA_TYPE",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					// Count distinct values of Usernames or CliVlaues in the respective columns
					String dataCustomersAffected = dbs.countDistinctRowsForSpecificColumnsNGAIncluded(dataSubsTable,
							new String[] { "PASPORT_COID" },
							hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullDataHierarchyPathSplit)),
							hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullDataHierarchyPathSplit)),
							hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullDataHierarchyPathSplit)),
							ngaTypes);

					// Count distinct values of Usernames or CliVlaues in the respective columns
					String IPTVCustomersAffected = dbs.countDistinctRowsForSpecificColumnsNGAIncluded(IPTVSubsTable,
							new String[] { "PASPORT_COID" },
							hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullIPTVHierarchyPathSplit)),
							hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullIPTVHierarchyPathSplit)),
							hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullIPTVHierarchyPathSplit)),
							ngaTypes);

					String voiceCustomersAffected = dbs.countDistinctRowsForSpecificColumnsNGAIncluded(voiceSubsTable,
							new String[] { "PASPORT_COID" },
							hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullVoiceHierarchyPathSplit)),
							hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullVoiceHierarchyPathSplit)),
							hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullVoiceHierarchyPathSplit)),
							ngaTypes);

					// For Voice no data customers are affected and vice versa
					if (service.equals("Voice"))
					{
						dataCustomersAffected = "0";
						IPTVCustomersAffected = "0";

						// Get Unique Locations affected from Voice_Resource_Path
						List<String> myList = dbs.getOneColumnUniqueResultSet("Prov_Voice_Resource_Path", "SiteName",
								hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
										fullVoiceHierarchyPathSplit)),
								hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
										fullVoiceHierarchyPathSplit)),
								hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(
										myHier.get(i).toString(), fullVoiceHierarchyPathSplit)));
						locationsAffectedList.addAll(myList);

					} else if (service.equals("Data"))
					{
						voiceCustomersAffected = "0";
						IPTVCustomersAffected = "0";

						// Get Unique Locations affected from Internet_Resource_Path
						List<String> myList = dbs.getOneColumnUniqueResultSet("Prov_Internet_Resource_Path", "SiteName",
								hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
										fullDataHierarchyPathSplit)),
								hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
										fullDataHierarchyPathSplit)),
								hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(
										myHier.get(i).toString(), fullDataHierarchyPathSplit)));
						locationsAffectedList.addAll(myList);

					} else if (service.equals("IPTV"))
					{
						dataCustomersAffected = "0";
						voiceCustomersAffected = "0";

						// Get Unique Locations affected from Internet_Resource_Path
						List<String> myList = dbs.getOneColumnUniqueResultSet("Prov_IPTV_Resource_Path", "SiteName",
								hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
										fullIPTVHierarchyPathSplit)),
								hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
										fullIPTVHierarchyPathSplit)),
								hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(
										myHier.get(i).toString(), fullIPTVHierarchyPathSplit)));
						locationsAffectedList.addAll(myList);
					}

					// Pick Unique values from locationsAffectedList
					uniqueLocationsSet = new HashSet<String>(locationsAffectedList);

					// Concatenating uniqueLocationsSet with pipe delimeter
					if (uniqueLocationsSet.size() > 0)
					{
						locationsAffected = String.join("|", uniqueLocationsSet);
						//						System.out.println("locationsAffected = " + locationsAffected);
					} else
					{
						locationsAffected = "none";
					}

					incidentDataCustomersAffected += Integer.parseInt(dataCustomersAffected);
					incidentVoiceCustomersAffected += Integer.parseInt(voiceCustomersAffected);
					incidentIPTVCustomersAffected += Integer.parseInt(IPTVCustomersAffected);
				}
			}

			// Check if for the same Incident ID, Service & Hierarchy - We have already an
			// entry
			for (String service : servicesAffected)
			{
				for (int i = 0; i < myHier.size(); i++)
				{
					boolean incidentAlreadyExists = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
							new String[] { "IncidentStatus", "IncidentID", "AffectedServices", "HierarchySelected" },
							new String[] { "OPEN", IncidentID, service, myHier.get(i).toString() },
							new String[] { "String", "String", "String", "String" });

					if (incidentAlreadyExists)
					{
						throw new InvalidInputException("There is already an openned incident (" + IncidentID
								+ ") that defines outage for AffectedService = " + service + " and HierarchySelected = "
								+ myHier.get(i).toString(), "Error 195");
					}
				}
			}

			// Calculate Sum of Voice/Data Customers affected for potentially already
			// opened same incident
			String numberOfVoiceCustAffectedFromPreviousIncidents = "0";
			String numberOfDataCustAffectedFromPreviousIncidents = "0";
			String numberOfIPTVCustAffectedFromPreviousIncidents = "0";

			if (s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents", new String[] { "IncidentID" },
					new String[] { IncidentID }, new String[] { "String" }))
			{
				numberOfVoiceCustAffectedFromPreviousIncidents = s_dbs.maxNumberOfCustomersAffected(
						"Test_SubmittedIncidents", "IncidentAffectedVoiceCustomers", new String[] { "IncidentID" },
						new String[] { IncidentID });
				numberOfDataCustAffectedFromPreviousIncidents = s_dbs.maxNumberOfCustomersAffected(
						"Test_SubmittedIncidents", "IncidentAffectedDataCustomers", new String[] { "IncidentID" },
						new String[] { IncidentID });
				numberOfIPTVCustAffectedFromPreviousIncidents = s_dbs.maxNumberOfCustomersAffected(
						"Test_SubmittedIncidents", "IncidentAffectedIPTVCustomers", new String[] { "IncidentID" },
						new String[] { IncidentID });

			}

			// Calculate Per Incident CLIsAffected
			int CLIsAffectedPerIncident = 0;

			for (int i = 0; i < myHier.size(); i++)
			{

				// Firstly determine the hierarchy table that will be used based on the root
				// hierarchy provided
				String rootHierarchySelected = hf.getRootHierarchyNode(myHier.get(i).toString());

				String fullVoiceHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
						"VoiceSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
						new String[] { rootHierarchySelected }, new String[] { "String" });

				String[] fullVoiceHierarchyPathSplit = fullVoiceHierarchyPath.split("->");

				// Secondly determine NGA_TYPE based on rootElement
				String ngaTypes = dbs.getOneValue("HierarchyTablePerTechnology2", "NGA_TYPE",
						new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
						new String[] { "String" });

				// Determine Tables for Data/Voice subscribers
				String dataSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "DataSubscribersTableName",
						new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
						new String[] { "String" });

				String IPTVSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "IPTVSubscribersTableName",
						new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
						new String[] { "String" });

				String voiceSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "VoiceSubscribersTableName",
						new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
						new String[] { "String" });

				// Calculate CLIs Affected but replace column names in order to search table for
				// customers affected
				String CLIsAffected_String = dbs.countDistinctCLIsAffected(new String[] { "PASPORT_COID" },
						hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
								fullVoiceHierarchyPathSplit)),
						hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
								fullVoiceHierarchyPathSplit)),
						hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
								fullVoiceHierarchyPathSplit)),
						ngaTypes, AffectedServices, voiceSubsTable, dataSubsTable, IPTVSubsTable);
				CLIsAffectedPerIncident += Integer.parseInt(CLIsAffected_String);
			}

			String CLIsAffected = String.valueOf(CLIsAffectedPerIncident);

			for (String service : servicesAffected)
			{
				for (int i = 0; i < myHier.size(); i++)
				{
					// Check Hierarchy Format Key_Value Pairs
					hf.checkHierarchyFormatKeyValuePairs(myHier.get(i).toString());

					// Firstly determine the hierarchy table that will be used based on the root
					// hierarchy provided
					String rootHierarchySelected = hf.getRootHierarchyNode(myHier.get(i).toString());

					// Determine Tables for Data/Voice subscribers
					String dataSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "DataSubscribersTableName",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					String IPTVSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "IPTVSubscribersTableName",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					String voiceSubsTable = dbs.getOneValue("HierarchyTablePerTechnology2", "VoiceSubscribersTableName",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					// Get Hierarchies for Data/Voice Tables
					String fullDataHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
							"DataSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					String[] fullDataHierarchyPathSplit = fullDataHierarchyPath.split("->");

					String fullIPTVHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
							"IPTVSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					String[] fullIPTVHierarchyPathSplit = fullIPTVHierarchyPath.split("->");

					String fullVoiceHierarchyPath = dbs.getOneValue("HierarchyTablePerTechnology2",
							"VoiceSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
							new String[] { rootHierarchySelected }, new String[] { "String" });

					String[] fullVoiceHierarchyPathSplit = fullVoiceHierarchyPath.split("->");

					// Secondly determine NGA_TYPE based on rootElement
					String ngaTypes = dbs.getOneValue("HierarchyTablePerTechnology2", "NGA_TYPE",
							new String[] { "RootHierarchyNode" }, new String[] { rootHierarchySelected },
							new String[] { "String" });

					// Count distinct values of Usernames or CliVlaues the respective columns
					String dataCustomersAffected = dbs.countDistinctRowsForSpecificColumnsNGAIncluded(dataSubsTable,
							new String[] { "PASPORT_COID" },
							hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullDataHierarchyPathSplit)),
							hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullDataHierarchyPathSplit)),
							hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullDataHierarchyPathSplit)),
							ngaTypes);

					String IPTVCustomersAffected = dbs.countDistinctRowsForSpecificColumnsNGAIncluded(IPTVSubsTable,
							new String[] { "PASPORT_COID" },
							hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullIPTVHierarchyPathSplit)),
							hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullIPTVHierarchyPathSplit)),
							hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullIPTVHierarchyPathSplit)),
							ngaTypes);

					String voiceCustomersAffected = dbs.countDistinctRowsForSpecificColumnsNGAIncluded(voiceSubsTable,
							new String[] { "PASPORT_COID" },
							hf.hierarchyKeys(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullVoiceHierarchyPathSplit)),
							hf.hierarchyValues(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullVoiceHierarchyPathSplit)),
							hf.hierarchyStringTypes(hf.replaceHierarchyForSubscribersAffected(myHier.get(i).toString(),
									fullVoiceHierarchyPathSplit)),
							ngaTypes);

					// For Voice no data customers are affected and vice versa
					if (service.equals("Voice"))
					{
						dataCustomersAffected = "0";
						IPTVCustomersAffected = "0";
					} else if (service.equals("Data"))
					{
						voiceCustomersAffected = "0";
						IPTVCustomersAffected = "0";
					} else if (service.equals("IPTV"))
					{
						dataCustomersAffected = "0";
						voiceCustomersAffected = "0";
					}

					// Get Max again
					OutageID_Integer = s_dbs.getMaxIntegerValue("Test_SubmittedIncidents", "OutageID");

					// Add One
					OutageID_Integer += 1;

					// Convert it to String (only for the sake of the below method
					// (InsertValuesInTableGetSequence) - In the database it is still an integer
					String OutageID_String = Integer.toString(OutageID_Integer);

					// Sum Customers Affected from Previous but same Incidents that were inserted in
					// the
					// past
					int totalVoiceIncidentAffected = incidentVoiceCustomersAffected
							+ Integer.parseInt(numberOfVoiceCustAffectedFromPreviousIncidents);
					int totalDataIncidentAffected = incidentDataCustomersAffected
							+ Integer.parseInt(numberOfDataCustAffectedFromPreviousIncidents);
					int totalIPTVIncidentAffected = incidentIPTVCustomersAffected
							+ Integer.parseInt(numberOfIPTVCustAffectedFromPreviousIncidents);

					// Concatenate locations with pipe
					locationsAffected = String.join("|", uniqueLocationsSet);

					// Backup Eligible - Addition of 23 Apr 2021
					String backupEligible = "";

					if (Impact.equals("LoS") && (service.equals("Voice") || service.equals("Data")))
					{
						backupEligible = "Yes";
					} else
					{
						backupEligible = "No";
					}

					// Insert Values in Database
					try
					{
						s_dbs.insertValuesInTable("Test_SubmittedIncidents",
								new String[] { "OpenReqID", "DateTime", "WillBePublished", "BackupEligible", "OutageID",
										"IncidentStatus", "RequestTimestamp", "SystemID", "UserID", "IncidentID",
										"Scheduled", "StartTime", "EndTime", "Duration", "AffectedServices", "Impact",
										"Priority", "HierarchySelected", "Locations", "AffectedVoiceCustomers",
										"AffectedDataCustomers", "AffectedCLICustomers", "ActiveDataCustomersAffected",
										"TVCustomersAffected", "IncidentAffectedVoiceCustomers",
										"IncidentAffectedDataCustomers", "IncidentAffectedIPTVCustomers" },
								new String[] { RequestID, hf.now(), "Yes", backupEligible, OutageID_String, "OPEN",
										RequestTimestamp, SystemID, UserID, IncidentID, Scheduled, StartTime, EndTime,
										Duration, service, Impact, Priority, myHier.get(i).toString(),
										locationsAffected, voiceCustomersAffected, dataCustomersAffected, CLIsAffected,
										"0", IPTVCustomersAffected, Integer.toString(totalVoiceIncidentAffected),
										Integer.toString(totalDataIncidentAffected),
										Integer.toString(totalIPTVIncidentAffected) },
								new String[] { "String", "DateTime", "String", "String", "Integer", "String",
										"DateTime", "String", "String", "String", "String", "DateTime", "DateTime",
										"String", "String", "String", "String", "String", "String", "Integer",
										"Integer", "Integer", "Integer", "Integer", "Integer", "Integer", "Integer" });
					} catch (SQLException e)
					{
						throw new InvalidInputException("An Error occured during submission of data!", "Error 1500");
					}

					if (Integer.parseInt(OutageID_String) > 0)
					{
						// Concatenate locations with comma
						locationsAffected = String.join(", ", uniqueLocationsSet);

						ProductOfSubmission ps = new ProductOfSubmission(RequestID, OutageID_String, IncidentID,
								voiceCustomersAffected, dataCustomersAffected, IPTVCustomersAffected, CLIsAffected,
								locationsAffected, Integer.toString(totalVoiceIncidentAffected),
								Integer.toString(totalDataIncidentAffected),
								Integer.toString(totalIPTVIncidentAffected), "1", service, myHier.get(i).toString(),
								"Submitted Successfully");
						prodElementsList.add(ps);

						// Production of the CSV Exported File for the Closed Incident.
						OpenningIncidentOutageToCSV OIATCSV = new OpenningIncidentOutageToCSV(dbs, s_dbs, IncidentID,
								OutageID_String);
						OIATCSV.produceReport();

						logger.info(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Submitted Outage: INCID: "
								+ IncidentID + " | OutageID: " + OutageID_String);
					}
				}
			}

			return prodElementsList;
		} catch (Exception e)
		{
			throw e;
		} finally
		{
			// Send Similar request to Spectra_Reporting server
			try
			{
				Async_SubmitOutage sOut = new Async_SubmitOutage(UserName, Password, RequestID, RequestTimestamp,
						SystemID, UserID, IncidentID, Scheduled, StartTime, EndTime, Duration, AffectedServices, Impact,
						Priority, HierarchySelected);
				sOut.run();
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try
			{
				logger.trace(
						req.getRemoteAddr() + " - ReqID: " + RequestID + " - Submit Outage: Closing DB Connection");
				if (conObj != null)
				{
					conObj.closeDBConnection();
				}
				if (s_conObj != null)
				{
					s_conObj.closeDBConnection();
				}

				conn = null;
				conObj = null;
				dbs = null;
				s_conn = null;
				s_conObj = null;
				s_dbs = null;
				mc = null;
				req = null;

			} catch (Exception e)
			{
				logger.info("Submit Outage Finally block");
			}
		}
	}

	@Override
	@WebMethod
	@WebResult(name = "Result")
	public List<ProductOfGetOutage> getOutageStatus(
			@WebParam(name = "UserName", header = true, mode = Mode.IN) String UserName,
			@WebParam(name = "Password", header = true, mode = Mode.IN) String Password,
			@WebParam(name = "RequestID") @XmlElement(required = true) String RequestID,
			@WebParam(name = "IncidentID") @XmlElement(required = true) String IncidentID,
			@WebParam(name = "IncidentStatus") @XmlElement(required = true) String IncidentStatus)
			throws Exception, InvalidInputException
	{
		Logger logger = LogManager.getLogger(gr.wind.spectra.web.WebSpectra.class.getName());
		Connection conn = null;
		DB_Connection conObj = null;
		Connection s_conn = null;
		s_DB_Connection s_conObj = null;
		s_DB_Operations s_dbs = null;
		MessageContext mc = null;
		HttpServletRequest req = null;
		//System.out.println("Client IP = " + req.getRemoteAddr());

		if (conn == null)
		{
			try
			{
				conObj = new DB_Connection();
				conn = conObj.connect();
				new DB_Operations(conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		if (s_conn == null)
		{
			try
			{
				s_conObj = new s_DB_Connection();
				s_conn = s_conObj.connect();
				s_dbs = new s_DB_Operations(s_conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		new WebSpectra();

		try
		{
			Help_Func hf = new Help_Func();

			// Those 2 directives is for IP retrieval of web request
			mc = wsContext.getMessageContext();
			req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

			////establishDBConnection();
			//establishStaticTablesDBConnection();

			logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID
					+ " - Get Outage Status: Establishing DB Connection");
			List<ProductOfGetOutage> prodElementsList;
			prodElementsList = new ArrayList<>();

			// Check if fields are empty
			hf.validateNotEmpty("IncidentID", IncidentID);
			hf.validateNotEmpty("IncidentStatus", IncidentStatus);

			// Check if Authentication credentials are correct.
			if (!s_dbs.authenticateRequest(UserName, Password, "test_remedyService"))
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ "Get Outage Status: - Wrong credentials provided - UserName: " + UserName + " Password: "
						+ Password);
				throw new InvalidInputException("User name or Password incorrect!", "Error 100");
			}

			// Update Statistics
			s_dbs.updateUsageStatisticsForMethod("GetOutageStatus");

			String numOfRows = "0";
			ResultSet rs = null;
			if (IncidentID.equals("*"))
			{
				// Number of rows that will be returned
				// numOfRows = dbs.NumberOfRowsFound("Test_SubmittedIncidents", "IncidentStatus =
				// '" + IncidentStatus + "'");

				numOfRows = s_dbs.numberOfRowsFound("Test_SubmittedIncidents", new String[] { "IncidentStatus" },
						new String[] { IncidentStatus }, new String[] { "String" });

				rs = s_dbs.getRows("Test_SubmittedIncidents",
						new String[] { "OutageID", "IncidentStatus", "RequestTimestamp", "SystemID", "UserID",
								"IncidentID", "Scheduled", "StartTime", "EndTime", "Duration", "AffectedServices",
								"Impact", "Priority", "Hierarchyselected", "AffectedVoiceCustomers",
								"AffectedDataCustomers", "AffectedCLICustomers", "ActiveDataCustomersAffected",
								"TVCustomersAffected", "IncidentAffectedVoiceCustomers",
								"IncidentAffectedDataCustomers" },
						new String[] { "IncidentStatus" }, new String[] { IncidentStatus }, new String[] { "String" });
			} else
			{
				numOfRows = s_dbs.numberOfRowsFound("Test_SubmittedIncidents",
						new String[] { "IncidentID", "IncidentStatus" }, new String[] { IncidentID, IncidentStatus },
						new String[] { "String", "String" });

				rs = s_dbs.getRows("Test_SubmittedIncidents",
						new String[] { "OutageID", "IncidentStatus", "RequestTimestamp", "SystemID", "UserID",
								"IncidentID", "Scheduled", "StartTime", "EndTime", "Duration", "AffectedServices",
								"Impact", "Priority", "Hierarchyselected", "AffectedVoiceCustomers",
								"AffectedDataCustomers", "AffectedCLICustomers", "ActiveDataCustomersAffected",
								"TVCustomersAffected", "IncidentAffectedVoiceCustomers",
								"IncidentAffectedDataCustomers" },
						new String[] { "IncidentID", "IncidentStatus" }, new String[] { IncidentID, IncidentStatus },
						new String[] { "String", "String" });
			}
			if (Integer.parseInt(numOfRows) == 0)
			{
				throw new InvalidInputException("No Results found", "No Results found according to your criteria");
			} else
			{
				while (rs.next())
				{
					ProductOfGetOutage pg = new ProductOfGetOutage(RequestID, rs.getString("OutageID"),
							rs.getString("IncidentStatus"), rs.getString("RequestTimestamp"), rs.getString("SystemID"),
							rs.getString("UserID"), rs.getString("IncidentID"), rs.getString("Scheduled"),
							rs.getString("StartTime"), rs.getString("EndTime"), rs.getString("Duration"),
							rs.getString("AffectedServices"), rs.getString("Impact"), rs.getString("Priority"),
							rs.getString("Hierarchyselected"), rs.getString("AffectedVoiceCustomers"),
							rs.getString("AffectedDataCustomers"), rs.getString("AffectedCLICustomers"),
							rs.getString("ActiveDataCustomersAffected"), rs.getString("TVCustomersAffected"),
							rs.getString("IncidentAffectedVoiceCustomers"),
							rs.getString("IncidentAffectedDataCustomers")

					);
					prodElementsList.add(pg);
				}
			}
			return prodElementsList;
		} catch (Exception e)
		{
			throw e;
		} finally
		{
			try
			{
				logger.trace(
						req.getRemoteAddr() + " - ReqID: " + RequestID + " - Get Outage Status: Closing DB Connection");
				if (conObj != null)
				{
					conObj.closeDBConnection();
				}
				if (s_conObj != null)
				{
					s_conObj.closeDBConnection();
				}
				conn = null;
				conObj = null;
				s_conn = null;
				s_conObj = null;
				s_dbs = null;
				mc = null;
				req = null;

			} catch (Exception e)
			{
				logger.info("Get Outage Status Finally block");
			}
		}
	}

	@Override
	@WebMethod
	@WebResult(name = "Result")
	public ProductOfModify modifyOutage(@WebParam(name = "UserName", header = true, mode = Mode.IN) String UserName,
			@WebParam(name = "Password", header = true, mode = Mode.IN) String Password,
			@WebParam(name = "RequestID") @XmlElement(required = true) String RequestID,
			@WebParam(name = "RequestTimestamp") @XmlElement(required = true) String RequestTimestamp,
			@WebParam(name = "SystemID") @XmlElement(required = true) String SystemID,
			@WebParam(name = "UserID") @XmlElement(required = true) String UserID,
			@WebParam(name = "IncidentID") @XmlElement(required = true) String IncidentID,
			@WebParam(name = "OutageID") @XmlElement(required = true) String OutageID,
			@WebParam(name = "StartTime") @XmlElement(required = false) String StartTime,
			@WebParam(name = "EndTime") @XmlElement(required = false) String EndTime,
			@WebParam(name = "Duration") @XmlElement(required = false) String Duration,
			// Quality, Loss
			@WebParam(name = "Impact") @XmlElement(required = false) String Impact)
			throws Exception, InvalidInputException
	{
		Logger logger = LogManager.getLogger(gr.wind.spectra.web.WebSpectra.class.getName());
		Connection conn = null;
		DB_Connection conObj = null;
		Connection s_conn = null;
		s_DB_Connection s_conObj = null;
		s_DB_Operations s_dbs = null;
		MessageContext mc = null;
		HttpServletRequest req = null;
		//System.out.println("Client IP = " + req.getRemoteAddr());
		//System.out.println("Client IP = " + req.getRemoteAddr());

		if (conn == null)
		{
			try
			{
				conObj = new DB_Connection();
				conn = conObj.connect();
				new DB_Operations(conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		if (s_conn == null)
		{
			try
			{
				s_conObj = new s_DB_Connection();
				s_conn = s_conObj.connect();
				s_dbs = new s_DB_Operations(s_conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		new WebSpectra();

		try
		{
			Help_Func hf = new Help_Func();

			// Those 2 directives is for IP retrieval of web request
			mc = wsContext.getMessageContext();
			req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

			////establishDBConnection();
			//establishStaticTablesDBConnection();

			logger.trace(
					req.getRemoteAddr() + " - ReqID: " + RequestID + " - Modify Outage: Establishing DB Connection");
			// Check if Authentication credentials are correct.
			if (!s_dbs.authenticateRequest(UserName, Password, "test_remedyService"))
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ "Modify Outage: - Wrong credentials provided - UserName: " + UserName + " Password: "
						+ Password);
				throw new InvalidInputException("User name or Password incorrect!", "Error 100");
			}

			// Update Statistics
			s_dbs.updateUsageStatisticsForMethod("ModifyOutage");

			ProductOfModify pom = null;

			// Check if Required fields are empty
			hf.validateNotEmpty("RequestID", RequestID);
			hf.validateNotEmpty("RequestTimestamp", RequestTimestamp);
			hf.validateDateTimeFormat("RequestTimestamp", RequestTimestamp);
			hf.validateNotEmpty("SystemID", SystemID);
			hf.validateNotEmpty("UserID", UserID);
			hf.validateNotEmpty("IncidentID", IncidentID);
			hf.validateNotEmpty("OutageID", OutageID);

			// if Start Time Value Exists
			if (!hf.checkIfEmpty("StartTime", StartTime))
			{
				// Check if it has the appropriate format
				hf.validateDateTimeFormat("StartTime", StartTime);
			}
			// if End Time Value Exists
			if (!hf.checkIfEmpty("EndTime", EndTime))
			{
				// Check if it has the appropriate format
				hf.validateDateTimeFormat("EndTime", EndTime);
			}

			// if Impact Value Exists
			if (!hf.checkIfEmpty("Impact", Impact))
			{
				// Check if it has the appropriate format
				hf.validateAgainstPredefinedValues("Impact", Impact, new String[] { "QoS", "LoS" });
			}

			// if Duration Value Exists
			if (!hf.checkIfEmpty("Duration", Duration))
			{
				// Check if it has the appropriate format
				hf.validateIntegerOrEmptyValue("Duration", Duration);
			}

			// Check if the combination of IncidentID & OutageID exists
			boolean incidentPlusOutageExists = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
					new String[] { "IncidentID", "OutageID" }, new String[] { IncidentID, OutageID },
					new String[] { "String", "String" });

			if (incidentPlusOutageExists)
			{
				// Check if the combination of IncidentID & OutageID refers to a scheduled
				// Incident (Scheduled = "Yes")
				boolean incidentIsScheduled = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
						new String[] { "IncidentID", "OutageID", "Scheduled" },
						new String[] { IncidentID, OutageID, "Yes" }, new String[] { "String", "String", "String" });
				// Create a new list with the updated columns - based on what is empty or not
				List<String> listOfColumnsForUpdate = new ArrayList<>();
				List<String> listOfValuesForUpdate = new ArrayList<>();
				List<String> listOfDataTypesForUpdate = new ArrayList<>();

				if (!hf.checkIfEmpty("StartTime", StartTime))
				{
					listOfColumnsForUpdate.add("StartTime");
					listOfValuesForUpdate.add(StartTime);
					listOfDataTypesForUpdate.add("Date");

					listOfColumnsForUpdate.add("ModifyReqID");
					listOfValuesForUpdate.add(RequestID);
					listOfDataTypesForUpdate.add("String");
				}

				if (!hf.checkIfEmpty("EndTime", EndTime))
				{
					listOfColumnsForUpdate.add("EndTime");
					listOfValuesForUpdate.add(EndTime);
					listOfDataTypesForUpdate.add("Date");

					listOfColumnsForUpdate.add("ModifyReqID");
					listOfValuesForUpdate.add(RequestID);
					listOfDataTypesForUpdate.add("String");
				}

				if (!hf.checkIfEmpty("Impact", Impact))
				{
					listOfColumnsForUpdate.add("Impact");
					listOfValuesForUpdate.add(Impact);
					listOfDataTypesForUpdate.add("String");

					listOfColumnsForUpdate.add("ModifyReqID");
					listOfValuesForUpdate.add(RequestID);
					listOfDataTypesForUpdate.add("String");
				}

				if (!hf.checkIfEmpty("Duration", Duration))
				{
					listOfColumnsForUpdate.add("Duration");
					listOfValuesForUpdate.add(Duration);
					listOfDataTypesForUpdate.add("Integer");

					listOfColumnsForUpdate.add("ModifyReqID");
					listOfValuesForUpdate.add(RequestID);
					listOfDataTypesForUpdate.add("String");
				}

				String[] arrayOfColumnsForUpdate = listOfColumnsForUpdate
						.toArray(new String[listOfColumnsForUpdate.size()]);
				String[] arrayOfValuesForUpdate = listOfValuesForUpdate
						.toArray(new String[listOfValuesForUpdate.size()]);
				String[] arrayOfDataTypesForUpdate = listOfDataTypesForUpdate
						.toArray(new String[listOfDataTypesForUpdate.size()]);

				// Update Start/End Times ONLY for Scheduled Incidents
				if (!incidentIsScheduled
						&& (!hf.checkIfEmpty("StartTime", StartTime) || (!hf.checkIfEmpty("EndTime", EndTime))))
				{
					throw new InvalidInputException(
							"The fields of 'Star Time'/'End Time' cannot be modified on non scheduled Outages (Incident: "
									+ IncidentID + ", OutageID " + OutageID + " is not a scheduled incident)",
							"Error 385");
				}

				// Check if Incident is still open
				boolean isIncidentClosed = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
						new String[] { "IncidentStatus", "IncidentID", "OutageID" },
						new String[] { "CLOSED", IncidentID, OutageID }, new String[] { "String", "String", "String" });

				if (isIncidentClosed)
				{
					throw new InvalidInputException(
							"The combination of IncidentID/OutageID is already closed and it cannot be modified - IncidentID: "
									+ IncidentID + " / OutageID: " + OutageID,
							"Error 715");
				}

				// Update Operation
				int numOfRowsUpdated = 0;
				try
				{
					numOfRowsUpdated = s_dbs.updateColumnOnSpecificCriteria("Test_SubmittedIncidents",
							arrayOfColumnsForUpdate, arrayOfValuesForUpdate, arrayOfDataTypesForUpdate,
							new String[] { "IncidentID", "OutageID" }, new String[] { IncidentID, OutageID },
							new String[] { "String", "Integer" });
				} catch (Exception e)
				{
					throw new InvalidInputException("An Error occured during modification of data!", "Error 1500");
				}

				if (numOfRowsUpdated == 1)
				{
					pom = new ProductOfModify(RequestID, IncidentID, OutageID, "930", "Successfully Modified Incident");
					logger.info(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Modify Outage: " + OutageID
							+ " -> " + Arrays.toString(arrayOfColumnsForUpdate) + " -> "
							+ Arrays.toString(arrayOfValuesForUpdate));
				} else
				{
					// System.out.println("Modifying: numOfRowsUpdated = " + numOfRowsUpdated);
					pom = new ProductOfModify(RequestID, IncidentID, OutageID, "980", "Error modifying incident!");
				}
			} else
			{
				throw new InvalidInputException("The combination of IncidentID: " + IncidentID + " and OutageID: "
						+ OutageID + " does not exist!", "Error 550");
			}

			// Return instance of class ProductOfModify
			return pom;
		} catch (Exception e)
		{
			throw e;
		} finally
		{
			// Send Similar request to Spectra_Reporting server
			try
			{
				Async_ModifyOutage mOut = new Async_ModifyOutage(UserName, Password, RequestID, RequestTimestamp,
						SystemID, UserID, IncidentID, OutageID, StartTime, EndTime, Duration, Impact);
				mOut.run();
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try
			{
				// Close DB Connection
				logger.trace(
						req.getRemoteAddr() + " - ReqID: " + RequestID + " - Modify Outage: Closing DB Connection");
				if (conObj != null)
				{
					conObj.closeDBConnection();
				}
				if (s_conObj != null)
				{
					s_conObj.closeDBConnection();
				}

				conn = null;
				conObj = null;
				s_conn = null;
				s_conObj = null;
				s_dbs = null;
				mc = null;
				req = null;

			} catch (Exception e)
			{
				logger.info("Modify Outage Finally block");
			}
		}
	}

	@Override
	@WebMethod
	@WebResult(name = "Result")
	public ProductOfCloseOutage closeOutage(@WebParam(name = "UserName", header = true, mode = Mode.IN) String UserName,
			@WebParam(name = "Password", header = true, mode = Mode.IN) String Password,
			@WebParam(name = "RequestID") @XmlElement(required = true) String RequestID,
			@WebParam(name = "RequestTimestamp") @XmlElement(required = true) String RequestTimestamp,
			@WebParam(name = "SystemID") @XmlElement(required = true) String SystemID,
			@WebParam(name = "UserID") @XmlElement(required = true) String UserID,
			@WebParam(name = "IncidentID") @XmlElement(required = true) String IncidentID,
			@WebParam(name = "OutageID") @XmlElement(required = true) String OutageID)
			throws Exception, InvalidInputException
	{
		Help_Func hf = new Help_Func();

		Logger logger = LogManager.getLogger(gr.wind.spectra.web.WebSpectra.class.getName());
		Connection conn = null;
		DB_Connection conObj = null;
		DB_Operations dbs = null;
		Connection s_conn = null;
		s_DB_Connection s_conObj = null;
		s_DB_Operations s_dbs = null;
		MessageContext mc = null;
		HttpServletRequest req = null;
		//System.out.println("Client IP = " + req.getRemoteAddr());
		//System.out.println("Client IP = " + req.getRemoteAddr());

		if (conn == null)
		{
			try
			{
				conObj = new DB_Connection();
				conn = conObj.connect();
				dbs = new DB_Operations(conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		if (s_conn == null)
		{
			try
			{
				s_conObj = new s_DB_Connection();
				s_conn = s_conObj.connect();
				s_dbs = new s_DB_Operations(s_conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		new WebSpectra();
		int numOfRowsUpdated = 0;
		try
		{
			// Those 2 directives is for IP retrieval of web request
			mc = wsContext.getMessageContext();
			req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

			//establishDBConnection();
			//establishStaticTablesDBConnection();

			logger.trace(
					req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: Establishing DB Connection");
			// Check if Authentication credentials are correct.
			if (!s_dbs.authenticateRequest(UserName, Password, "test_remedyService"))
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ " - Close Outage: - Wrong credentials provided - UserName: " + UserName + " Password: "
						+ Password);
				throw new InvalidInputException("User name or Password incorrect!", "Error 100");
			}

			// Update Statistics
			s_dbs.updateUsageStatisticsForMethod("CloseOutage");

			ProductOfCloseOutage poca = null;

			// Check if Required fields are empty
			hf.validateNotEmpty("RequestID", RequestID);
			hf.validateNotEmpty("RequestTimestamp", RequestTimestamp);
			hf.validateDateTimeFormat("RequestTimestamp", RequestTimestamp);
			hf.validateNotEmpty("SystemID", SystemID);
			hf.validateNotEmpty("UserID", UserID);
			hf.validateNotEmpty("IncidentID", IncidentID);
			hf.validateNotEmpty("OutageID", OutageID);

			// Check if the combination of IncidentID & OutageID exists
			boolean incidentPlusOutageExists = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
					new String[] { "IncidentID", "OutageID" }, new String[] { IncidentID, OutageID },
					new String[] { "String", "String" });

			if (incidentPlusOutageExists)
			{
				logger.info(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: for INCID: " + IncidentID
						+ " OutageID: " + OutageID);

				// Check if the combination of IncidentID & OutageID is still OPEN
				boolean incidentPlusOutageIsOpen = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
						new String[] { "IncidentID", "OutageID", "IncidentStatus" },
						new String[] { IncidentID, OutageID, "OPEN" }, new String[] { "String", "String", "String" });

				// If incident is still in status OPEN
				if (incidentPlusOutageIsOpen)
				{
					// Check if the Incidents is Scheduled
					boolean incidentIsScheduled = s_dbs.checkIfCriteriaExists("Test_SubmittedIncidents",
							new String[] { "IncidentID", "OutageID", "IncidentStatus", "Scheduled" },
							new String[] { IncidentID, OutageID, "OPEN", "Yes" },
							new String[] { "String", "String", "String", "String" });

					// If it is scheduled then the End Time should NOT be updated
					if (incidentIsScheduled)
					{
						logger.info(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: INCID: "
								+ IncidentID + " | OutageID: " + OutageID + " is OPEN & Scheduled");

						try
						{
							// Update Operation
							numOfRowsUpdated = s_dbs.updateColumnOnSpecificCriteria("Test_SubmittedIncidents",
									new String[] { "IncidentStatus" }, new String[] { "CLOSED" },
									new String[] { "String" }, new String[] { "IncidentID", "OutageID" },
									new String[] { IncidentID, OutageID }, new String[] { "String", "Integer" });
						} catch (Exception e)
						{
							throw new InvalidInputException("An Error occured during closure/submission of data!",
									"Error 1500");
						}

					} else
					{
						logger.info(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: INCID: "
								+ IncidentID + " | OutageID: " + OutageID + " is OPEN & not Scheduled");

						try
						{
							// If it is NOT scheduled then the End Time should be updated
							numOfRowsUpdated = s_dbs.updateColumnOnSpecificCriteria("Test_SubmittedIncidents",
									new String[] { "IncidentStatus", "EndTime", "CloseReqID" },
									new String[] { "CLOSED", hf.now(), RequestID },
									new String[] { "String", "Date", "String" },
									new String[] { "IncidentID", "OutageID" }, new String[] { IncidentID, OutageID },
									new String[] { "String", "Integer" });
						} catch (Exception e)
						{
							throw new InvalidInputException("An Error occured during closure/submission of data!",
									"Error 1500");
						}
					}

					// Only one line should always be updated in this operation
					if (numOfRowsUpdated == 1)
					{
						logger.info(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: INCID: "
								+ IncidentID + "| OutageID: " + OutageID + " successfully CLOSED");

						// Production of the CSV Exported File for the Closed Incident.
						IncidentOutageToCSV IOCSV = new IncidentOutageToCSV(dbs, s_dbs, IncidentID, OutageID);
						IOCSV.produceReport();

						poca = new ProductOfCloseOutage(RequestID, IncidentID, OutageID, "990",
								"Successfully Closed Incident");
					} else
					{
						logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: INCID: "
								+ IncidentID + "| OutageID: " + OutageID + " FAILED (more than 1 lines updated)");
						poca = new ProductOfCloseOutage(RequestID, IncidentID, OutageID, "423",
								"Error Closing Incident");
					}
				} else // If incident is not in status OPEN
				{

					String closedTime = s_dbs.getOneValue("Test_SubmittedIncidents", "EndTime",
							new String[] { "IncidentID", "OutageID" }, new String[] { IncidentID, OutageID },
							new String[] { "String", "String" });
					logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: INCID: "
							+ IncidentID + " | OutageID: " + OutageID + " is already closed since: " + closedTime);
					throw new InvalidInputException("The combination of IncidentID: " + IncidentID + " and OutageID: "
							+ OutageID + " has already been closed since: " + closedTime, "Error 820");
				}
			} else
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ " - Close Outage: The combination of IncidentID: " + IncidentID + " | OutageID: " + OutageID
						+ " does not exist");
				throw new InvalidInputException("The combination of IncidentID: " + IncidentID + " and OutageID: "
						+ OutageID + " does not exist!", "Error 950");
			}

			// Send Similar request to Spectra_Reporting server
			try
			{
				Async_CloseOutage cOut = new Async_CloseOutage(UserName, Password, RequestID, RequestTimestamp,
						SystemID, UserID, IncidentID, OutageID);
				cOut.run();
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return poca;
		} catch (Exception e)
		{
			throw e;
		} finally
		{
			try
			{
				logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID + " - Close Outage: Closing DB Connection");
				if (conObj != null)
				{
					conObj.closeDBConnection();
				}
				if (s_conObj != null)
				{
					s_conObj.closeDBConnection();
				}

				conn = null;
				conObj = null;
				dbs = null;
				s_conn = null;
				s_conObj = null;
				s_dbs = null;
				mc = null;
				req = null;

			} catch (Exception e)
			{
				logger.info("Close Outage Finally block");
			}
		}
	}

	@Override
	@WebMethod
	@WebResult(name = "Result")
	public ProductOfNLUActive NLU_Active(@WebParam(name = "UserName", header = true, mode = Mode.IN) String UserName,
			@WebParam(name = "Password", header = true, mode = Mode.IN) String Password,
			@WebParam(name = "RequestID") @XmlElement(required = true) String RequestID,
			@WebParam(name = "SystemID") @XmlElement(required = true) String SystemID,
			@WebParam(name = "RequestTimestamp") @XmlElement(required = true) String RequestTimestamp,
			@WebParam(name = "CLI") @XmlElement(required = true) String CLI,
			@WebParam(name = "Service") @XmlElement(required = false) String Service,
			@WebParam(name = "ServiceL2") @XmlElement(required = false) String ServiceL2,
			@WebParam(name = "ServiceL3") @XmlElement(required = false) String ServiceL3)
			throws Exception, InvalidInputException
	{
		Logger logger = LogManager.getLogger(gr.wind.spectra.web.WebSpectra.class.getName());
		Connection conn = null;
		DB_Connection conObj = null;
		DB_Operations dbs = null;
		Connection s_conn = null;
		s_DB_Connection s_conObj = null;
		s_DB_Operations s_dbs = null;
		MessageContext mc = null;
		HttpServletRequest req = null;
		//System.out.println("Client IP = " + req.getRemoteAddr());
		//System.out.println("Client IP = " + req.getRemoteAddr());

		if (conn == null)
		{
			try
			{
				conObj = new DB_Connection();
				conn = conObj.connect();
				dbs = new DB_Operations(conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		if (s_conn == null)
		{
			try
			{
				s_conObj = new s_DB_Connection();
				s_conn = s_conObj.connect();
				s_dbs = new s_DB_Operations(s_conn);
			} catch (Exception ex)
			{
				logger.fatal("Could not open connection with database!");
				throw new Exception(ex.getMessage());
			}
		}

		new WebSpectra();
		ProductOfNLUActive ponla = null;
		try
		{
			Help_Func hf = new Help_Func();

			// Those 2 directives is for IP retrieval of web request
			mc = wsContext.getMessageContext();
			req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

			//establishDBConnection();
			//establishStaticTablesDBConnection();

			logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID + " - NLU Active: Establishing DB Connection");
			logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID + " - NLU Active: Question for CLI Outage of "
					+ CLI);
			// Check if Authentication credentials are correct.
			if (!s_dbs.authenticateRequest(UserName, Password, "test_nluService"))
			{
				logger.error(req.getRemoteAddr() + " - ReqID: " + RequestID
						+ "NLU Active: - Wrong credentials provided - UserName: " + UserName + " Password: "
						+ Password);
				throw new InvalidInputException("User name or Password incorrect!", "Error 100");
			}

			// Check if Required fields are empty
			hf.validateNotEmpty("RequestID", RequestID);
			hf.validateNotEmpty("SystemID", SystemID);
			hf.validateNotEmpty("RequestTimestamp", RequestTimestamp);
			hf.validateDateTimeFormat("RequestTimestamp", RequestTimestamp);
			hf.validateNotEmpty("CLI", CLI);
			// Help_Func.validateNotEmpty("Service", Service);

			// if Impact Value Exists
			if (!hf.checkIfEmpty("Service", Service))
			{
				// Check if it has the appropriate format
				hf.validateDelimitedValues("Service", Service, "\\|", new String[] { "Voice", "Data", "IPTV" });
			}

			Test_CLIOutage co = new Test_CLIOutage(dbs, s_dbs, RequestID);
			ponla = co.checkCLIOutage(RequestID, CLI, Service);

		} catch (Exception e)
		{
			throw e;
		} finally
		{
			// Send Similar request to Spectra_Reporting server
			try
			{
				Async_NLUActive nluA = new Async_NLUActive(UserName, Password, RequestID, RequestTimestamp, SystemID,
						CLI, Service, ServiceL2, ServiceL3);
				nluA.run();
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try
			{
				logger.trace(req.getRemoteAddr() + " - ReqID: " + RequestID + " - NLU Active: Closing DB Connection");
				if (conObj != null)
				{
					conObj.closeDBConnection();
				}
				if (s_conObj != null)
				{
					s_conObj.closeDBConnection();
				}

				conn = null;
				conObj = null;
				dbs = null;
				s_conn = null;
				s_conObj = null;
				s_dbs = null;
				mc = null;
				req = null;

			} catch (Exception e)
			{
				logger.info("NLU Active Finally block");
			}
		}
		return ponla;
	}
}