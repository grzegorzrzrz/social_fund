package app;

import classes.*;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.*;
import java.util.ArrayList;
import java.util.Vector;

public class Database {
    private final String DBURL;
    private final String DBUSERNAME;
    private final String DBPASSWORD;
    private Connection con;
    private Statement stmt;

    public Database(String url, String user, String password)
    {
        DBURL = url;
        DBUSERNAME = user;
        DBPASSWORD = password;
    }

    /**
     * @param sql sql query
     * @return Result of sql query, connection need to be closed after extracting result
     */
    private ResultSet Select(String sql) {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            con = DriverManager.getConnection(DBURL, DBUSERNAME, DBPASSWORD);
            stmt = con.createStatement();
            return stmt.executeQuery(sql);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param dml insert or update operation to be executed in database
     */
    private void Dml(String dml) {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            con = DriverManager.getConnection(DBURL, DBUSERNAME, DBPASSWORD);
            stmt = con.createStatement();
            stmt.executeUpdate(dml);
            stmt.close();
            con.close();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * @param procedure procedure to be executed in database
     */
    private void Procedure(String procedure) {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            con = DriverManager.getConnection(DBURL, DBUSERNAME, DBPASSWORD);
            stmt = con.createStatement();
            stmt.execute(procedure);
            stmt.close();
            con.close();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param condName name of data type in database
     * @param data fragment of searched data
     * @return condition for sql query
     */
    private String AddCondition(String condName, String data) {
        return condName + " like '%" + data + "%'";
    }

     /**
     * @param userID id of user
     * @return Array of integers with all form types ids
     */
    public Vector<Pair<Integer, Date>> GetApplicationsByUserID(int userID) {
        String sql = "select id_wniosku, data_zlozenia" +
                "from WNIOSEK" +
                "where id_uzytkownika=" + userID;
        return GetApplicationsVector(sql);
    }

    /**
     * @return Array of integers with all form types ids
     */
    public Vector<Pair<Integer, Date>>  GetPendingApplications() {
        String sql = "select id_wniosku, data_zlozenia" +
                "from WNIOSEK" +
                "where status='Rozpatrywany'";
        return GetApplicationsVector(sql);
    }

    /**
     * @return Array of integers with all form types ids
     */
    public void DeactivateFormType(int formID) {
        String sql = "UPDATE typ SET status = 'Nieaktywny' WHERE id_typu_wniosku = " + formID;
        Dml(sql);
    }

    /**
     * @param sql sql query
     * @return Array of integers with all form types ids
     */
    private Vector<Pair<Integer, Date>>  GetApplicationsVector(String sql) {
        try {
            ResultSet rs = Select(sql);
            Vector<Pair<Integer, Date>> applications = new Vector<Pair<Integer, Date>>();
            while (rs.next()) {
                applications.add(Pair.of(rs.getInt(1), rs.getDate(2)));
            }
            rs.close();
            stmt.close();
            con.close();
            return applications;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param applicationID id of application
     * @return application with given id
     */
    public Application GetApplicationInfo(int applicationID) {
        String sql = "SELECT * FROM WNIOSEK WHERE id_wniosku = " + applicationID;
        Application application = GetApplicationInfo(sql).get(0);
        return application;
    }
    public Applicant GetApplicantInfo(int applicantID) {
        Applicant applicant = new Applicant();
        applicant.setId(applicantID);
        String sql = "SELECT * FROM wnioskodawcy WHERE wnioskodawcy_id_uzytkownika = " + applicantID;
        GetApplicant(sql, applicant);
        sql = "SELECT dochod_na_czlonka_rodziny FROM oswiadczenie_zarobkowe WHERE wnioskodawcy_id_uzytkownika = " + applicantID;
        GetEarnings(sql, applicant);
        return applicant;
    }
    private void GetEarnings(String sql, Applicant applicant) {
        try {
            ResultSet rs = Select(sql);
            while (rs.next()) {
                applicant.setEarnings(rs.getInt(1));
            }
            rs.close();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    private void GetApplicant(String sql, Applicant applicant) {
        try {
            ResultSet rs = Select(sql);
            while (rs.next()) {
                applicant.setCompany(rs.getString(2));
                applicant.setPesel(rs.getString(3));
                applicant.setBirthDate(rs.getDate(4));
                applicant.setAccountNumber(rs.getInt(5));
            }
            rs.close();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param startDate start date of application
     * @param endDate end date of application
     * @param status status of application
     * @return applications with given id
     */
    public ArrayList<Application> GetApplicationInfo(Date startDate, Date endDate, String status) {
        String sql = "SELECT * FROM WNIOSEK WHERE  data_zlozenia >= TO_DATE('" +
                startDate + "','YYYY-MM-DD') AND data_zlozenia <= TO_DATE('" + endDate + "','YYYY-MM-DD')";
        if (!status.equals("Any")) {
            sql += " AND status = '" + status + "'";
        }
        return GetApplicationInfo(sql);
    }

    /**
     * @param startDate start date of application
     * @param endDate end date of application
     * @param status status of application
     * @param formName fund of application
     * @return report with all valid applications
     */
    public StringBuilder GenerateReport(Date startDate, Date endDate, String status, String formName) {
        ArrayList<Application> applications = GetApplicationInfo(startDate, endDate, status);
        StringBuilder report = new StringBuilder();
        report.append("Status,Data złożenia,Typ wniosku,Typ funduszu,Pola\n");
        for (Application application : applications) {
            if(formName.equals("All") || application.getForm().getName().equals(formName)) {
                report.append(application.getStatus());
                report.append(",");
                report.append(application.getCreationDate());
                report.append(",");
                report.append(application.getForm().getName());
                report.append(",");
                report.append(application.getForm().getFundName());
                report.append(",{");
                for (FormField field : application.getForm().getFields()) {
                    report.append(field.getName());
                    report.append(",");
                    report.append(field.getValue());
                    report.append(",");
                    report.append(field.getMaximumLength());
                    report.append(",");
                    report.append(field.getType());
                }
                report.append("}\n");
            }
        }
        return report;
    }

    /**
     * @param sql sql query
     * @return ArrayList<Application> list of applications
     */
    private ArrayList<Application> GetApplicationInfo(String sql) {
        try {
            ResultSet rs = Select(sql);
            ArrayList<Application> applications = new ArrayList<>();
            while (rs.next()) {
                applications.add(GetApplicationFromResult(rs));
            }
            rs.close();
            stmt.close();
            con.close();
            return applications;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param rs result from sql query
     * @return list of applications
     * @throws SQLException wrong sql query
     */
    private Application GetApplicationFromResult(ResultSet rs) throws SQLException {
        String fund = rs.getString(1);
        int applicationID = rs.getInt(2);
        String status = rs.getString(3);
        Date creationDate = rs.getDate(4);
        Form form = GetFormFromString(rs.getString(5));
        int applicantID = rs.getInt(6);
        Application application = new Application(GetApplicantInfo(applicantID), status, creationDate, form);
        return application;
    }

    /**
     * Decodes encoded application form
     * @param encodedFields encoded form
     * @return decoded form
     */
    private Form GetFormFromString(String encodedFields) throws SQLException {
        ArrayList<FormField> fields = new ArrayList<>();
        String[] fieldsArray = encodedFields.split(";");
        int formTypeID = Integer.parseInt(fieldsArray[0]);
        String formName = GetFormName(formTypeID);
        String fundName = fieldsArray[1];

        for (int i = 2; i < fieldsArray.length; i++) {
            String[] fieldParts = fieldsArray[i].split(":");
            int fieldID = Integer.parseInt(fieldParts[0]);
            String value = fieldParts[1];
            String name = fieldParts[2];
            String type = fieldParts[3];
            int maximumLength = Integer.parseInt(fieldParts[4]);
            FormField formField = new FormField(name, type, value, maximumLength);
            fields.add(formField);
        }
        return new Form(formName, fundName, fields);
    }

    public String[] GetFormTypes() {
        String sql = "SELECT nazwa_formularzu FROM typ_formularzu";
        try {
            ResultSet rs = Select(sql);
            ArrayList<String> formTypes = new ArrayList<>();
            while (rs.next()) {
                formTypes.add(rs.getString(1));
            }
            rs.close();
            stmt.close();
            con.close();
            return formTypes.toArray(new String[0]);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return array of Form objects
     */
    private ArrayList<Form> GetForms() {
        String sql = "select * from typ_formularzu";
        try {
            ResultSet rs = Select(sql);
            ArrayList<Form> forms = new ArrayList<>();
            while (rs.next()) {
                int formTypeID = rs.getInt(1);
                String formName = rs.getString(2);
                ArrayList<FormField> formFields = GetFormFields(formTypeID);
                forms.add(new Form(formName, "TODO", formFields));
            }
            rs.close();
            stmt.close();
            con.close();
            return forms;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param formTypeID id of form type
     * @return array of FormField objects
     */
    private ArrayList<FormField> GetFormFields(int formTypeID) {
        String sql = "select pf.nazwa_pola, tpf.typ_danej,  tpf.maksymalna_dlugosc from pola_formularzu pf join typ_pol_formularzu tpf on" +
                " pf.typ_pol_formularzu_id_typu = tpf.typ_pol_formularzu where pf.typ_formularzu_id_formularzu = " + formTypeID;
        try {
            ResultSet rs = Select(sql);
            ArrayList<FormField> formFields = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString(1);
                String type = rs.getString(2);
                int maxLength = rs.getInt(3);
                String value = "";
                formFields.add(new FormField(name, type, value, maxLength));
            }
            rs.close();
            stmt.close();
            con.close();
            return formFields;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param form form template to be added to database
     * @return message
     */
    public String AddForm(Form form) {
        String fieldNames = "";
        String fieldTypes = "";
        String fieldMaxLengths = "";
        for (FormField field : form.getFields()) {
            fieldNames += field.getName() + ",";
            fieldTypes += field.getType() + ",";
            fieldMaxLengths += field.getMaximumLength() + ",";
        }
        String sql = "call DodajFormularz(" + form.getName() + ", '" + form.getFundName() + "', '" + fieldNames + "', '" + fieldTypes + "', '" + fieldMaxLengths + "')";
        Procedure(sql);
        return "Successfully added a new form";
    }

    /**
     * @param application application to be added to database
     */
    private void AddApplication(Application application, int userID) {
        String sql = "call DodajWniosek(" + application.getForm().getFundName() + ", '" + GetStringFromForm(application.getForm()) + "', " + userID + ")";
        Procedure(sql);
    }

    /**
     * @param applicationID id of application
     * @param status status to be set
     */
    private void SetApplicationStatus(int applicationID, String status){
        String sql = "call UstawStatusWniosku(" + applicationID + ", '" + status + "')";
        Procedure(sql);
    }

    /**
     * @param applicationID id of application
     * @param status status to be set
     * @param reviewerID id of reviewer
     */
    private void SetApplicationStatus(int applicationID, String status, int reviewerID){
        String sql = "call UstawStatusWniosku(" + applicationID + ", '" + status + "', " + reviewerID + ")";
        Procedure(sql);
    }

    /**
     * @param fundName name of fund
     * @return current value of fund
     */
    private int GetCurrentFundValue(int fundName){
        String sql = "select kwota_przyznana, kwota_uzyta, kwota_z_poprzedniego_roku from fundusz where typ_srodkow_encja_slownikowa_nazwa_funduszu = " + fundName;
        try {
            ResultSet rs = Select(sql);
            rs.next();
            int value = rs.getInt(1) - rs.getInt(2) + rs.getInt(3);
            rs.close();
            stmt.close();
            con.close();
            return value;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param fundName name of fund
     * @param value value to be added
     */
    private void WithdrawFunds(int fundName, int value){
        String sql = "call WyplacSrodki(" + fundName + ", " + value + ")";
        Procedure(sql);
    }

    /**
     * @param form form to be encoded
     * @return encoded form
     */
    private String GetStringFromForm(Form form) {
        String encodedFields = form.getName() + ";";
        for (FormField field : form.getFields()) {
            encodedFields += field.getName() + ":" + field.getValue() + ":" + field.getType() + ":" + field.getMaximumLength() + ";";
        }
        return encodedFields;
    }

    /**
     * @param formTypeID id of form
     * @return name of form
     * @throws SQLException wrong sql query
     */
    private String GetFormName(int formTypeID) throws SQLException {
        String sql = "SELECT nazwa_formularzu FROM typ_formularzu WHERE id_formularzu = " + formTypeID;
        ResultSet rs = Select(sql);
        rs.next();
        return rs.getString(1);
    }

    /**
     * @param login user login
     * @param password user password
     * @return user with given login and password
     */
    public void GetUser(User user) {
        String sql = "SELECT * FROM uzytkownicy WHERE login = '" + user.getLogin() + "' AND haslo = '" + user.getPassword() + "'";
        try {
            ResultSet rs = Select(sql);
            if(rs.next()) {
                user.setUserID(rs.getInt(1));
                rs.close();
                user.setPermissionLevel(1);

                // Check for admin
                String sqlCheckAdmin = "SELECT * FROM rozpatrujacy WHERE id_uzytkownika = '" + user.getUserID() + "'";
                try {
                    ResultSet rsCheckAdmin = Select(sqlCheckAdmin);
                    if (rsCheckAdmin.next()) {
                        user.setPermissionLevel(2);
                    }
                    rsCheckAdmin.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            stmt.close();
            con.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param login user login
     * @param password user password
     * @param name user name
     * @param surname user surname
     * @param company user company
     * @param pesel user pesel
     * @param birthDate user birth date
     */
    private void AddNewUser(String login, String password, String name, String surname, String company, String pesel,
                            Date birthDate) {
        this.AddNewUser(login, password, name, surname, company, pesel, birthDate, "");
    }

    /**
     * @param login user login
     * @param password user password
     * @param name user name
     * @param surname user surname
     * @param company user company
     * @param pesel user pesel
     * @param birthDate user birthdate
     * @param accountNumber user account number
     */
    private void AddNewUser(String login, String password, String name, String surname, String company, String pesel,
                            Date birthDate, String accountNumber) {
        String procedure = "call DodajWnioskodawce('" + login + "', '" + password + "', '" + name + "', '" + surname + "', '" +
                company + "', '" + pesel + "', '" + birthDate + "', '" + accountNumber + "')";
        Procedure(procedure);
    }

    /**
     * @param userID user id
     * @param income user income
     */
    private void AddUserIncome(int userID, String income) {
        String procedure = "call DodajDochodWnioskodawcy(" + userID + ", '" + income + "')";
        Procedure(procedure);
    }
}
