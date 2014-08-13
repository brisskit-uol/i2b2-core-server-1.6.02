package edu.harvard.i2b2.crc.loader.dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.BatchSqlUpdate;

import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.crc.loader.datavo.loader.DataSourceLookup;
import edu.harvard.i2b2.crc.loader.datavo.loader.Patient;
import edu.harvard.i2b2.crc.loader.datavo.pdo.ParamType;
import edu.harvard.i2b2.crc.loader.datavo.pdo.PatientType;

/**
 * Patient Dimension data access object.
 * 
 * @author rk903
 */
public class PatientDAO extends CRCLoaderDAO implements IPatientDAO {

	private int DB_BATCH_INSERT_SIZE = 2000;

	private static Log log = LogFactory.getLog(PatientDAO.class);
	
	private static DatatypeFactory datatypeFactory = null ;
    static {
        try {
            datatypeFactory = DatatypeFactory.newInstance() ;
        } 
        catch( DatatypeConfigurationException dce ) {
            throw new IllegalStateException( "Failed to obtain instance of DatatypeFactory", dce) ;
        }
    }
	
	private DataSourceLookup dataSourceLookup = null;

	public PatientDAO(DataSourceLookup dataSourceLookup, DataSource ds) {
		setDataSource(ds);
		setDbSchemaName(dataSourceLookup.getFullSchema());
		this.dataSourceLookup = dataSourceLookup;

	}

	public int getRecordCountByUploadId(int uploadId) {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(getDataSource());
		int insertCount = jdbcTemplate.queryForInt("select count(1) from "
				+ getDbSchemaName() + "patient_dimension where upload_id =?",
				new Object[] { uploadId });
		return insertCount;
	}

	/**
	 * Function to create temp visit dimension table using stored proc.
	 * 
	 * @param tempTableName
	 * @throws Exception
	 */
	public void createTempTable(String tempPatientTableName,
			String tempPatientMappingTableName) throws I2B2Exception {
		Connection conn = null;
		try {
			conn = getDataSource().getConnection();
			CallableStatement callStmt = conn.prepareCall("{call "
					+ getDbSchemaName() + "CREATE_TEMP_PATIENT_TABLE(?,?)}");
			callStmt.setString(1, tempPatientTableName);
			callStmt.registerOutParameter(2, java.sql.Types.VARCHAR);
			// callStmt.setString(2, tempPatientMappingTableName);
			callStmt.execute();
			this.getSQLServerProcedureError(dataSourceLookup.getServerType(),
					callStmt, 2);
		} catch (SQLException sqlEx) {
			sqlEx.printStackTrace();
			throw new I2B2Exception(
					"SQLException occured" + sqlEx.getMessage(), sqlEx);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new I2B2Exception("Exception occured" + ex.getMessage(), ex);
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException sqlEx) {
					sqlEx.printStackTrace();
					log.error("Error while closing connection", sqlEx);
				}
			}
		}
	}

	/**
	 * Create batch insert handle for temp observation fact table.
	 * 
	 * @param tempTableName
	 * @return
	 */
	public TempPatientDimensionInsertHandler createTempPatientDimensionInsert(
			String tempTableName) {
		TempPatientInsert tempPatientInsert = new TempPatientInsert(
				getDataSource(), tempTableName, getDbSchemaName());
		tempPatientInsert.setBatchSize(DB_BATCH_INSERT_SIZE);
		return new TempPatientDimensionInsertHandler(tempPatientInsert);
	}

	/**
	 * Function to create new encounter/visit from temp_visit_dimension table
	 * using stored proc.
	 * 
	 * @param tempTableName
	 * @throws Exception
	 */
	public void createPatientFromTempTable(String tempTableName,
			String tempMapTableName, int uploadId) throws I2B2Exception {
		Connection conn = null;
		try {
			conn = getDataSource().getConnection();
			CallableStatement callStmt = conn.prepareCall("{call "
					+ getDbSchemaName() + "INSERT_PATIENT_FROMTEMP(?,?,?)}");
			callStmt.setString(1, tempTableName);
			callStmt.setInt(2, uploadId);
			callStmt.registerOutParameter(3, java.sql.Types.VARCHAR);
			callStmt.execute();
			this.getSQLServerProcedureError(dataSourceLookup.getServerType(),
					callStmt, 3);
		} catch (SQLException sqlEx) {
			sqlEx.printStackTrace();
			throw new I2B2Exception(
					"SQLException occured" + sqlEx.getMessage(), sqlEx);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new I2B2Exception("Exception occured" + ex.getMessage(), ex);
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException sqlEx) {
					sqlEx.printStackTrace();
					log.error("Error while closing connection", sqlEx);
				}
			}
		}
	}

	// ************* Operation Objects section ***************

	/**
	 * <code>Patient</code> Insert Object.
	 */
	protected class TempPatientInsert extends BatchSqlUpdate {

		/**
		 * Create a new instance of PatientInsert.
		 * 
		 * @param ds
		 *            the DataSource to use for the insert
		 */
		protected TempPatientInsert( DataSource ds, String tableName, String schemaName ) {
			super(ds, "INSERT INTO " + schemaName + tableName + "  ("
					+ "patient_id," 
					+ "patient_id_source,"
					+ "vital_status_cd, "			
					+ "birth_date, " 
					+ "death_date, "
// JBL. Start of optional columns
					+ "sex_cd, "
					+ "age_in_years_num, " 
					+ "language_cd, " 
					+ "race_cd, "
					+ "marital_status_cd, " 
					+ "religion_cd, " 
					+ "zip_cd,  " 
					+ "statecityzip_path, " 
// JBL. End of optional columns
					+ "patient_blob,"
					+ "sourcesystem_cd, " 
					+ "update_date, " 
					+ "download_date, "
					+ "import_date)  "
					+ " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.TIMESTAMP));
			declareParameter(new SqlParameter(Types.TIMESTAMP));
// JBL. Start of optional columns
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.INTEGER));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
// JBL. End of optional columns
			declareParameter(new SqlParameter(Types.LONGVARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.TIMESTAMP));
			declareParameter(new SqlParameter(Types.TIMESTAMP));
			declareParameter(new SqlParameter(Types.TIMESTAMP));
			compile();
		}
		
		private Date getDate( String xmlDate ) {
			return datatypeFactory.newXMLGregorianCalendar( xmlDate ).toGregorianCalendar().getTime() ;
		}

		protected void insert(PatientType patient) {
			Map<String, ParamType> paramMap = null;

			if (patient.getParam() != null) {
				paramMap = buildNVParam(patient.getParam());
			} else {
				paramMap = new HashMap<String, ParamType>();
			}

			Object[] objs = new Object[] {
				(patient.getPatientId() != null) ? patient.getPatientId().getValue() : null,
				(patient.getPatientId() != null) ? patient.getPatientId().getSource() : null,
				(paramMap.get("age_in_years_num") != null) ? paramMap.get("age_in_years_num").getValue() : null, 
								
				/*
				 * Jeff Lusted (jl99@le.ac.uk)
				 * Fix for dates when taken as a param...
				 */
				(paramMap.get("birth_date") != null) ? getDate( paramMap.get("birth_date").getValue() ) : null,							
				(paramMap.get("death_date") != null) ? getDate( paramMap.get( "death_date").getValue() ) : null,
									
				(paramMap.get("language_cd") != null) ? paramMap.get("language_cd").getValue() : null,						
				(paramMap.get("marital_status_cd") != null) ? paramMap.get("marital_status_cd").getValue() : null,						
				(paramMap.get("race_cd") != null) ? paramMap.get("race_cd").getValue() : null,
				(paramMap.get("religion_cd") != null) ? paramMap.get("religion_cd").getValue() : null,
				(paramMap.get("sex_cd") != null) ? paramMap.get("sex_cd").getValue() : null,
				(paramMap.get("vital_status_cd") != null) ? paramMap.get("vital_status_cd").getValue() : null,
				(paramMap.get("zip_cd") != null) ? paramMap.get("zip_cd").getValue() : null,
				(paramMap.get("statecityzip_path") != null) ? paramMap.get("statecityzip_path").getValue() : null,
				(patient.getPatientBlob() != null) ? patient.getPatientBlob().getContent().get(0) : null,
									
				patient.getSourcesystemCd(),
							
				( patient.getUpdateDate() != null ) ? patient.getUpdateDate().toGregorianCalendar().getTime() : null,
				( patient.getDownloadDate() != null ) ? patient.getDownloadDate().toGregorianCalendar().getTime() : null,
				( patient.getImportDate() != null ) ? patient.getImportDate().toGregorianCalendar().getTime() : null 

			};

			super.update(objs);
			// retrieveIdentity(patient);
		}
	}

	/**
	 * <code>Patient</code> Update Object.
	 */
	protected class PatientUpdate extends BatchSqlUpdate {

		/**
		 * Create a new instance of PatientUpdate.
		 * 
		 * @param ds
		 *            the DataSource to use for the update
		 */
		protected PatientUpdate(DataSource ds) {
			super(ds, "UPDATE patient_dimension SET " 					
				    + "vital_status_cd=?, " 
					+ "birth_date=?, " 
					+ "death_date=?, "
// JBL. Start of optional columns
					+ "sex_cd=?, " 					
					+ "age_in_years_num=?, "
					+ "language_cd=?, "
					+ "race_cd=?, "
					+ "marital_status_cd=?, " 
					+ "religion_cd=?, " 
					+ "zip_cd=? "
					+ "statecityzip_path=?, " 
// JBL. End of optional columns
					+ "sourcesystem_cd=?, "
					+ "update_date=?, "
					+ "download_date=?, "
					+ "import_date=?, " 					
					+ "WHERE patient_num=?");
			
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.DATE));
			declareParameter(new SqlParameter(Types.DATE));

			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.INTEGER));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.DATE));
			declareParameter(new SqlParameter(Types.DATE));
			declareParameter(new SqlParameter(Types.DATE));
			declareParameter(new SqlParameter(Types.VARCHAR));
			compile();
		}

		/**
		 * Method to update an <code>Patient</code>'s data.
		 * 
		 * @param patient
		 *            to update
		 * @return the number of rows affected by the update
		 */
		protected int update(Patient patient) {
			return this.update(new Object[] { 					
			        patient.getVitalStatusCd(),
					patient.getBirthDate(), 
					patient.getDeathDate(),
					
					patient.getSexCd(),
					patient.getAgeInYearsNum(),
					patient.getLanguageCd(), 
					patient.getRaceCd(), 
					patient.getMaritalStatusCd(),
					patient.getReligionCd(),
					patient.getZipCd(),
					patient.getStateCityZipPath(), 
					
					patient.getSourceSystemCd(),
					patient.getUpdateDate(),
					patient.getDownloadDate(), 
					patient.getImportDate(),
										
					patient.getPatientNum() });
		}
	}

	/**
	 * Patient_Mapping insert code.
	 */
	protected class PatientMappingInsert extends BatchSqlUpdate {

		/**
		 * Create a new instance of Patient_MappingInsert.
		 * 
		 * @param ds
		 *            the DataSource to use for the insert
		 */
		protected PatientMappingInsert(DataSource ds) {

			super(ds, "INSERT INTO patient_mapping (" + "patient_ide, "
					+ "patient_ide_source, " + "patient_ide_status, "
					+ "patient_num " + ")" + " VALUES(?,?,?)");
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.VARCHAR));
			declareParameter(new SqlParameter(Types.INTEGER));
			compile();
		}

		protected void insert(Patient patient) {
			Object[] objs = new Object[] { patient.getPatientIde(),
					patient.getSource(), patient.getPatientIdeStatus(),
					patient.getPatientNum() };
			super.update(objs);
		}
	}

}
