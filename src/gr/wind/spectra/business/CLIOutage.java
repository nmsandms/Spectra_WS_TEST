package gr.wind.spectra.business;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

//Import log4j classes.
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gr.wind.spectra.model.ProductOfNLUActive;
import gr.wind.spectra.web.InvalidInputException;

public class CLIOutage
{
	private DB_Operations dbs;
	private s_DB_Operations s_dbs;
	private String requestID;
	DateFormat dateFormat = new SimpleDateFormat(Help_Func.DATE_FORMAT);

	// Logger instance
	private static final Logger logger = LogManager.getLogger(gr.wind.spectra.business.CLIOutage.class.getName());

	public CLIOutage(DB_Operations dbs, s_DB_Operations s_dbs, String requestID) throws Exception
	{
		this.dbs = dbs;
		this.s_dbs = s_dbs;
		this.requestID = requestID;
	}

	public String replaceHierarchyColumns(String hierarchyProvided, String technology)
			throws SQLException, InvalidInputException
	{
		String newHierarchyValue = "";

		if (technology.equals("Voice"))
		{
			// Get root hierarchy String
			String rootElementInHierarchy = Help_Func.getRootHierarchyNode(hierarchyProvided);

			String fullVoiceSubsHierarchyFromDB;
			String[] fullVoiceSubsHierarchyFromDBSplit;
			// Get Full Voice hierarchy in style :
			// OltElementName->OltSlot->OltPort->Onu->ActiveElement->Slot
			fullVoiceSubsHierarchyFromDB = dbs.getOneValue("HierarchyTablePerTechnology2",
					"VoiceSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
					new String[] { rootElementInHierarchy }, new String[] { "String" });

			// Split the Data hierarchy retrieved from DB into fields
			fullVoiceSubsHierarchyFromDBSplit = fullVoiceSubsHierarchyFromDB.split("->");

			// Replace Hierarchy Columns from the relevant subscribers table
			newHierarchyValue = Help_Func.replaceHierarchyForSubscribersAffected(hierarchyProvided,
					fullVoiceSubsHierarchyFromDBSplit);
		} else if (technology.equals("Data"))
		{
			// Get root hierarchy String
			String rootElementInHierarchy = Help_Func.getRootHierarchyNode(hierarchyProvided);

			String fullVoiceSubsHierarchyFromDB;
			String[] fullVoiceSubsHierarchyFromDBSplit;
			// Get Full Voice hierarchy in style :
			// OltElementName->OltSlot->OltPort->Onu->ActiveElement->Slot
			fullVoiceSubsHierarchyFromDB = dbs.getOneValue("HierarchyTablePerTechnology2",
					"DataSubscribersTableNamePath", new String[] { "RootHierarchyNode" },
					new String[] { rootElementInHierarchy }, new String[] { "String" });

			// Split the Data hierarchy retrieved from DB into fields
			fullVoiceSubsHierarchyFromDBSplit = fullVoiceSubsHierarchyFromDB.split("->");

			// Replace Hierarchy Columns from the relevant subscribers table
			newHierarchyValue = Help_Func.replaceHierarchyForSubscribersAffected(hierarchyProvided,
					fullVoiceSubsHierarchyFromDBSplit);
		}
		return newHierarchyValue;
	}

	public ProductOfNLUActive checkCLIOutage(String RequestID, String CLIProvided, String ServiceType)
			throws SQLException, InvalidInputException, ParseException
	{
		ProductOfNLUActive ponla = new ProductOfNLUActive();
		boolean foundAtLeastOneCLIAffected = false;
		boolean voiceAffected = false;
		boolean dataAffected = false;
		String IncidentID = "";
		String allAffectedServices = "";

		// Check if we have at least one OPEN incident
		boolean weHaveOpenIncident = s_dbs.checkIfStringExistsInSpecificColumn("Test_SubmittedIncidents", "IncidentStatus",
				"OPEN");

		// Check number of open incidents
		String numOfOpenIncidentsCurrently = s_dbs.numberOfRowsFound("Test_SubmittedIncidents",
				new String[] { "IncidentStatus" }, new String[] { "OPEN" }, new String[] { "String" });

		// If the submitted service type is empty then fill it with "Voice|Data"
		if (Help_Func.checkIfEmpty("ServiceType", ServiceType))
		{
			ServiceType = "Voice|Data";
		}

		logger.info("ReqID: " + RequestID + " - Checking CLI Outage CLI: " + CLIProvided + " | " + ServiceType);

		// Split ServiceType
		String delimiterCharacter = "\\|";
		String[] ServiceTypeSplitted = ServiceType.split(delimiterCharacter);

		logger.debug("ReqID: " + RequestID + " - We have open incidents: " + weHaveOpenIncident);
		// If We have at least one opened incident...
		if (weHaveOpenIncident)
		{
			logger.debug(
					"ReqID: " + RequestID + " - Number of incidents currently OPEN: " + numOfOpenIncidentsCurrently);

			String HierarchySelected = "";
			String Priority = "";
			String outageAffectedService = "";
			String Scheduled = "";
			String Duration = "";
			Date StartTime = null;
			Date EndTime = null;
			String Impact = "";
			String EndTimeString = null;

			for (String service : ServiceTypeSplitted)
			{
				ResultSet rs = null;
				// Get Lines with IncidentStatus = "OPEN"
				rs = s_dbs.getRows("Test_SubmittedIncidents",
						new String[] { "WillBePublished", "IncidentID", "OutageID", "HierarchySelected", "Priority",
								"AffectedServices", "Scheduled", "Duration", "StartTime", "EndTime", "Impact" },
						new String[] { "IncidentStatus" }, new String[] { "OPEN" }, new String[] { "String" });

				while (rs.next())
				{
					boolean isOutageWithinScheduledRange = false;

					String WillBePublished = rs.getString("WillBePublished");
					IncidentID = rs.getString("IncidentID");
					int OutageID = rs.getInt("OutageID");
					HierarchySelected = rs.getString("HierarchySelected");
					Priority = rs.getString("Priority");
					outageAffectedService = rs.getString("AffectedServices");
					Scheduled = rs.getString("Scheduled");
					Duration = rs.getString("Duration");
					StartTime = rs.getTimestamp("StartTime");
					EndTime = rs.getTimestamp("EndTime");
					Impact = rs.getString("Impact");

					// If it is OPEN & Scheduled & Date(Now) > StartTime then set
					// isOutageWithinScheduledRange to TRUE
					if (Scheduled.equals("Yes"))
					{
						logger.debug("ReqID: " + RequestID + " - Checking Scheduled Incident: " + IncidentID);
						// Get current date
						LocalDateTime now = LocalDateTime.now();

						// Convert StartTime date to LocalDateTime object
						LocalDateTime StartTimeInLocalDateTime = Instant.ofEpochMilli(StartTime.getTime())
								.atZone(ZoneId.systemDefault()).toLocalDateTime();

						// Convert EndTime date to LocalDateTime object
						LocalDateTime EndTimeInLocalDateTime = Instant.ofEpochMilli(EndTime.getTime())
								.atZone(ZoneId.systemDefault()).toLocalDateTime();

						// if Start time is after NOW and End Time is Before NOW then we have outage
						if (now.isAfter(StartTimeInLocalDateTime) && now.isBefore(EndTimeInLocalDateTime))
						{
							isOutageWithinScheduledRange = true;
							logger.debug(
									"ReqID: " + RequestID + " - Scheduled Incident: " + IncidentID + " is ongoing");
						} else
						{
							isOutageWithinScheduledRange = false;
							logger.debug(
									"ReqID: " + RequestID + " - Scheduled Incident: " + IncidentID + " is NOT ongoing");
						}

						// If the scheduled period (Start Time - End Time) has passed current local time then change the Incident status to "CLOSED"
						/*  NOT TESTED YET
						if (now.isAfter(EndTimeInLocalDateTime))
						{
							int numOfRowsUpdated = s_dbs.updateColumnOnSpecificCriteria("Test_SubmittedIncidents",
									new String[] { "IncidentStatus" }, new String[] { "CLOSED" },
									new String[] { "String" }, new String[] { "IncidentID", "OutageID" },
									new String[] { IncidentID, String.valueOf(OutageID) },
									new String[] { "String", "Integer" });

							if (numOfRowsUpdated > 0)
							{
								logger.debug("ReqID: " + RequestID + " - Scheduled Incident: " + IncidentID
										+ " was marked as CLOSED");
							}
						}
						*/
					}

					// if service given in web request is Voice
					if (outageAffectedService.equals("Voice") && service.equals("Voice"))
					{
						// Replace Hierarchy keys from the correct column names of Hierarchy Subscribers
						// table
						HierarchySelected = this.replaceHierarchyColumns(HierarchySelected, "Voice");

						// Add CLI Value in Hierarcy
						HierarchySelected += "->CliValue=" + CLIProvided;

						// Get root hierarchy String
						String rootElementInHierarchy = Help_Func.getRootHierarchyNode(HierarchySelected);

						// Get Hierarchy Table for that root hierarchy
						String table = dbs.getOneValue("HierarchyTablePerTechnology2", "VoiceSubscribersTableName",
								new String[] { "RootHierarchyNode" }, new String[] { rootElementInHierarchy },
								new String[] { "String" });

						String numOfRowsFound = dbs.numberOfRowsFound(table, Help_Func.hierarchyKeys(HierarchySelected),
								Help_Func.hierarchyValues(HierarchySelected),
								Help_Func.hierarchyStringTypes(HierarchySelected));

						// If matched Hierarchy + CLI matches lines (then those CLIs have actually
						// Outage)
						if (WillBePublished.equals("Yes") && Integer.parseInt(numOfRowsFound) > 0
								&& Scheduled.equals("No"))
						{
							foundAtLeastOneCLIAffected = true;
							voiceAffected = true;
							logger.info("ReqID: " + RequestID + " - Found Affected CLI: " + CLIProvided + " | "
									+ ServiceType + " from Non-scheduled INC: " + IncidentID + " | OutageID: "
									+ OutageID + " | " + outageAffectedService);
							break;

						} else if (WillBePublished.equals("Yes") && Integer.parseInt(numOfRowsFound) > 0
								&& Scheduled.equals("Yes") && isOutageWithinScheduledRange)
						{
							foundAtLeastOneCLIAffected = true;
							voiceAffected = true;
							logger.info("ReqID: " + RequestID + " - Found Affected CLI: " + CLIProvided + " | "
									+ ServiceType + " from Scheduled INC: " + IncidentID + " | OutageID: " + OutageID
									+ " | " + outageAffectedService);
							break;
						}

					} else if (outageAffectedService.equals("Data") && service.equals("Data"))
					{
						// Replace Hierarchy keys from the correct column names of Hierarchy Subscribers
						// table
						HierarchySelected = this.replaceHierarchyColumns(HierarchySelected, "Data");

						// Add CLI Value in Hierarcy
						HierarchySelected += "->CliValue=" + CLIProvided;

						// Get root hierarchy String
						String rootElementInHierarchy = Help_Func.getRootHierarchyNode(HierarchySelected);

						// Get Hierarchy Table for that root hierarchy
						String table = dbs.getOneValue("HierarchyTablePerTechnology2", "DataSubscribersTableName",
								new String[] { "RootHierarchyNode" }, new String[] { rootElementInHierarchy },
								new String[] { "String" });

						String numOfRowsFound = dbs.numberOfRowsFound(table, Help_Func.hierarchyKeys(HierarchySelected),
								Help_Func.hierarchyValues(HierarchySelected),
								Help_Func.hierarchyStringTypes(HierarchySelected));

						// Scheduled No & Rows Found
						if (WillBePublished.equals("Yes") && Integer.parseInt(numOfRowsFound) > 0
								&& Scheduled.equals("No"))
						{
							foundAtLeastOneCLIAffected = true;
							dataAffected = true;
							logger.info("ReqID: " + RequestID + " - Found Affected CLI: " + CLIProvided + " | "
									+ ServiceType + " from Non-scheduled INC: " + IncidentID + " | OutageID: "
									+ OutageID + " | " + outageAffectedService);
							break;
							// Scheduled Yes & Rows Found & Outage Within Scheduled Range
						} else if (WillBePublished.equals("Yes") && Integer.parseInt(numOfRowsFound) > 0
								&& Scheduled.equals("Yes") && isOutageWithinScheduledRange)
						{
							foundAtLeastOneCLIAffected = true;
							dataAffected = true;
							logger.info("ReqID: " + RequestID + " - Found Affected CLI: " + CLIProvided + " | "
									+ ServiceType + " from Scheduled INC: " + IncidentID + " | OutageID: " + OutageID
									+ " | " + outageAffectedService);
							break;
						}
					}
				}
			}
			// CLI is not affected from outage
			if (!foundAtLeastOneCLIAffected)
			{
				logger.info("ReqID: " + RequestID + " - No Service affection for CLI: " + CLIProvided + " | "
						+ ServiceType);
				ponla = new ProductOfNLUActive(this.requestID, CLIProvided, "No", "none", "none", "none", "none",
						"none", "none", "none", "NULL", "NULL", "NULL");

				//throw new InvalidInputException("No service affection", "Info 425");
			} else
			{
				// Indicate Voice, Data or Voice|Data service affection
				if (voiceAffected && dataAffected)
				{
					allAffectedServices = "Voice|Data";
				} else if (voiceAffected)
				{
					allAffectedServices = "Voice";
				} else if (dataAffected)
				{
					allAffectedServices = "Data";
				}

				ponla = new ProductOfNLUActive(this.requestID, CLIProvided, "Yes", IncidentID, Priority,
						allAffectedServices, Scheduled, Duration, EndTimeString, Impact, "NULL", "NULL", "NULL");
			}

		} else
		{
			logger.info(
					"ReqID: " + RequestID + " - No Service affection for CLI: " + CLIProvided + " | " + ServiceType);
			//throw new InvalidInputException("No service affection", "Info 425");
			ponla = new ProductOfNLUActive(this.requestID, CLIProvided, "No", "none", "none", "none", "none", "none",
					"none", "none", "NULL", "NULL", "NULL");
		}

		return ponla;
	}

}