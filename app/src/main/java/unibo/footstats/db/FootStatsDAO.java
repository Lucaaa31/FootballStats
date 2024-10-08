package unibo.footstats.db;

import unibo.footstats.model.statistiche.PlayerStats;
import unibo.footstats.model.utente.User;
import unibo.footstats.utility.AccountType;
import unibo.footstats.utility.PlayerResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FootStatsDAO implements AutoCloseable {
    private final String DB_URL = "jdbc:mysql://localhost:3306/FootStats"; // Replace with your DB URL
    private final String USER = "root"; // Replace with your DB username
    private final String PASS = "12345"; // Replace with your DB password
    private Connection connection;

    public FootStatsDAO() {
        try {
            this.connection = DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // 1. Registrazione di un nuovo utente
    public boolean registerUser(final String nome,
                                final String cognome,
                                final String username,
                                final String password){
        String query = "INSERT INTO ACCOUNT (Nome, Cognome, Username, Password) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, nome);
            stmt.setString(2, cognome);
            stmt.setString(3, username);
            stmt.setString(4, password);
            stmt.executeUpdate();
        } catch (SQLException e) {
            return false;
        }

        String query2 = "INSERT INTO UTENTE (Username) VALUES (?)";

        try (PreparedStatement stmt = connection.prepareStatement(query2)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    // 2. Accesso di un utente
    public boolean login(final String username, final String password) throws SQLException {
        return true;
//        String query = "SELECT Password FROM ACCOUNT WHERE Username = ? ";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, username);
//            try (ResultSet rs = stmt.executeQuery()) {
//                rs.next();
//                return rs.getString("Password").equals(password);
//            } catch (SQLException e) {
//                return false;
//            }
//        }
    }

    // Account type
    public AccountType getAccountType(final String username) throws SQLException {
        final String query = "SELECT account.Username AS ACC_NAME,"
                + " utente.Username AS IS_USR,"
                + " amministratore.UsernameAmministratore AS IS_AMM "
                + "FROM account LEFT JOIN utente ON utente.Username = account.Username "
                + "LEFT JOIN amministratore ON amministratore.UsernameAmministratore = account.username\n"
                + "WHERE account.username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (!Objects.isNull(rs.getString("IS_USR"))) {
                        return AccountType.USER;
                    } else if (!Objects.isNull(rs.getString("IS_AMM"))) {
                        return AccountType.ADMIN;
                    } else {
                        throw new IllegalStateException("No account type found.");
                    }
                }
            }
        }
        return null;
    }

    // Account
    public User getAccount(final String username) throws SQLException {
        String query = "SELECT Nome, Cognome, Targhetta FROM account "
                + "LEFT JOIN utente ON account.Username = utente.Username WHERE account.Username = ? ";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (getAccountType(username) == AccountType.ADMIN) {
                        return new User(rs.getString("Nome"),
                                rs.getString("Cognome"),
                                username,
                                "Amministratore"
                        );
                    } else {
                        return new User(rs.getString("Nome"),
                                rs.getString("Cognome"),
                                username,
                                Objects.requireNonNullElse(rs.getString("Targhetta"), "N/A")
                        );
                    }
                }
            } catch (SQLException e) {
                throw new SQLException("Error while getting account");
            }

        }
        return null;
    }

    public List<String> getCompetitions() {
        final String query = "SELECT Nome FROM TIPO_COMPETIZIONE";
        final List<String> competitions = new ArrayList<>();
        competitions.add("Tutte le competizioni");

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                competitions.add(rs.getString("Nome"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return competitions;
    }

    public List<PlayerResult> searchPlayer(final String name) {
        final String query = "SELECT c.CF, c.Nome, c.Cognome, sgs.Ruolo, sp.NomeSquadra AS Squadra, c.Nazionalita, sgs.Valore_di_mercato " +
                "FROM Calciatore c " +
                "LEFT JOIN STATS_GIOCATORE_STAGIONE sgs ON c.CF = sgs.CF_Calciatore " +
                "LEFT JOIN STORICO_PARTECIPAZIONI sp ON c.CF = sp.CF_Calciatore " +
                "WHERE LOWER(c.Nome) LIKE CONCAT(LOWER(?), '%') OR LOWER(c.Cognome) LIKE CONCAT(LOWER(?), '%');";


        List<PlayerResult> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, name);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new PlayerResult(
                            rs.getString("CF"),
                            rs.getString("Nome"),
                            rs.getString("Cognome"),
                            rs.getString("Ruolo"),
                            Objects.isNull(rs.getString("Squadra")) ? "N/A" : rs.getString("Squadra"),
                            rs.getString("Nazionalita"),
                            rs.getString("Valore_di_mercato")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    public List<String> getSeasons() {
        final String query = "SELECT DISTINCT AnnoCalcistico FROM stagione";
        final List<String> seasons = new ArrayList<>();
        seasons.add("Tutte le stagioni");

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                seasons.add(rs.getString("AnnoCalcistico"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return seasons;
    }

    public PlayerStats getStatistics(final String cf, final String season, final String competition) {

        if (season == null && competition == null) {
            return careerOnly(cf);
        } else if (season != null && competition == null) {
            return seasonOnly(cf, season);
        } else if (season == null) {
            return competitionOnly(cf, competition);
        } else {
            return seasonAndCompetition(cf, season, competition);
        }


    }

    private PlayerStats competitionOnly(final String cf, final String competition) {
        final String query = "SELECT " +
                " cal.Nome, " +
                " cal.Cognome, " +
                " SUM(stats_partita.Goal) AS Goal, " +
                " SUM(stats_partita.Assist) AS Assist, " +
                " SUM(stats_partita.Cartellini) AS Cartellini, " +
                " stats_stagione.Valore_di_mercato AS ValoreMercato, " +
                " COUNT(stats_partita.CodicePartita) AS Presenze, " +
                " stats_stagione.Numero_maglia AS NumeroMaglia, " +
                " stats_stagione.Ruolo AS Ruolo " +
                "FROM CALCIATORE cal " +
                "JOIN " +
                "    STATS_GIOCATORE_PARTITA stats_partita " +
                "    ON cal.CF = stats_partita.CF_Calciatore " +
                "JOIN " +
                "    COMPETIZIONE comp " +
                "    ON stats_partita.AnnoCalcistico = comp.AnnoCalcistico " +
                "    AND stats_partita.TipoCompetizione = comp.TipoCompetizione " +
                "    AND stats_partita.CodiceCompetizione = comp.CodiceCompetizione " +
                "JOIN " +
                "    STATS_GIOCATORE_STAGIONE stats_stagione " +
                "    ON cal.CF = stats_stagione.CF_Calciatore " +
                "    AND stats_partita.AnnoCalcistico = stats_stagione.AnnoCalcistico " +
                "WHERE " +
                "    cal.CF = ? " +
                "    AND stats_partita.TipoCompetizione = ? " +
                "GROUP BY " +
                "    cal.Nome, cal.Cognome, stats_stagione.Valore_di_mercato, stats_stagione.Numero_maglia, stats_stagione.Ruolo;";


        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, cf);
            preparedStatement.setString(2, competition);
            PlayerStats rs = getPlayerStats(cf, preparedStatement);
            if (rs != null) return rs;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private PlayerStats careerOnly(final String cf) {
        final String query = "SELECT " +
                " cal.Nome, " +
                " cal.Cognome, " +
                " SUM(stats_giocatore_stagione.Goal_stagionali) AS Goal, " +
                " SUM(stats_giocatore_stagione.Assist_stagionali) AS Assist, " +
                " SUM(stats_giocatore_stagione.Cartellini_stagionali) AS Cartellini, " +
                " MAX(stats_giocatore_stagione.Valore_di_mercato) AS ValoreMercato, " +
                " SUM(stats_giocatore_stagione.Presenze) AS Presenze, " +
                " SUM(stats_giocatore_stagione.Numero_maglia) AS NumeroMaglia, " +
                " MAX(stats_giocatore_stagione.Ruolo) AS Ruolo " +
                "FROM CALCIATORE cal " +
                "JOIN " +
                "    STATS_GIOCATORE_STAGIONE stats_giocatore_stagione " +
                "    ON cal.CF = stats_giocatore_stagione.CF_Calciatore " +
                "WHERE " +
                "    cal.CF = ? ";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, cf);
            PlayerStats rs = getPlayerStats(cf, stmt);
            if (rs != null) return rs;
        } catch (SQLException e) {
            throw new RuntimeException(e);

        }

        return null;
    }


    private PlayerStats getPlayerStats(final String cf, final PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new PlayerStats(
                        cf,
                        rs.getString("Nome"),
                        rs.getString("Cognome"),
                        "Tutte le stagioni",
                        rs.getInt("Goal"),
                        rs.getInt("Assist"),
                        rs.getInt("Cartellini"),
                        rs.getDouble("ValoreMercato"),
                        rs.getInt("Presenze"),
                        rs.getInt("NumeroMaglia"),
                        rs.getString("Ruolo")
                );
            }
        }
        return null;
    }


    private PlayerStats seasonOnly(final String cf, final String season) {
        String query = "SELECT " +
                " cal.Nome, " +
                " cal.Cognome, " +
                " stats_giocatore_stagione.Goal_stagionali AS Goal, " +
                " stats_giocatore_stagione.Assist_stagionali AS Assist, " +
                " stats_giocatore_stagione.Cartellini_stagionali AS Cartellini, " +
                " stats_giocatore_stagione.Valore_di_mercato, " +
                " stats_giocatore_stagione.Presenze, " +
                " stats_giocatore_stagione.Numero_maglia, " +
                " stats_giocatore_stagione.Ruolo " +
                "FROM CALCIATORE cal " +
                "JOIN " +
                "    STATS_GIOCATORE_STAGIONE stats_giocatore_stagione " +
                "    ON cal.CF = stats_giocatore_stagione.CF_Calciatore " +
                "WHERE " +
                "    cal.CF = ? " +
                "    AND stats_giocatore_stagione.AnnoCalcistico = ?;";


        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, cf);
            stmt.setString(2, season);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(
                            cf,
                            rs.getString("Nome"),
                            rs.getString("Cognome"),
                            season,
                            rs.getInt("Goal"),
                            rs.getInt("Assist"),
                            rs.getInt("Cartellini"),
                            rs.getDouble("Valore_di_mercato"),
                            rs.getInt("Presenze"),
                            rs.getInt("Numero_maglia"),
                            rs.getString("Ruolo")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private PlayerStats seasonAndCompetition(final String cf, final String season, final String competition) {
        String query = "SELECT " +
                " cal.Nome, " +
                " cal.Cognome, " +
                " SUM(stats_partita.Goal) AS Goal, " +
                " SUM(stats_partita.Assist) AS Assist, " +
                " SUM(stats_partita.Cartellini) AS Cartellini, " +
                " stats_stagione.Valore_di_mercato, " +
                " COUNT(stats_partita.CodicePartita) AS Presenze, " +
                " stats_stagione.Numero_maglia, " +
                " stats_stagione.Ruolo " +
                "FROM CALCIATORE cal " +
                "JOIN " +
                "    STATS_GIOCATORE_PARTITA stats_partita " +
                "    ON cal.CF = stats_partita.CF_Calciatore " +
                "JOIN " +
                "    COMPETIZIONE comp " +
                "    ON stats_partita.AnnoCalcistico = comp.AnnoCalcistico " +
                "    AND stats_partita.TipoCompetizione = comp.TipoCompetizione " +
                "    AND stats_partita.CodiceCompetizione = comp.CodiceCompetizione " +
                "JOIN " +
                "    STATS_GIOCATORE_STAGIONE stats_stagione " +
                "    ON cal.CF = stats_stagione.CF_Calciatore " +
                "    AND stats_partita.AnnoCalcistico = stats_stagione.AnnoCalcistico " +
                "WHERE " +
                "    cal.CF = ? " +
                "    AND stats_partita.AnnoCalcistico = ? " +
                "    AND stats_partita.TipoCompetizione = ? " +
                "GROUP BY " +
                "    cal.Nome, cal.Cognome, stats_stagione.Valore_di_mercato, stats_stagione.Numero_maglia, stats_stagione.Ruolo;";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, cf);
            stmt.setString(2, season);
            stmt.setString(3, competition);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(
                            cf,
                            rs.getString("Nome"),
                            rs.getString("Cognome"),
                            season,
                            rs.getInt("Goal"),
                            rs.getInt("Assist"),
                            rs.getInt("Cartellini"),
                            rs.getDouble("Valore_di_mercato"),
                            rs.getInt("Presenze"),
                            rs.getInt("Numero_maglia"),
                            rs.getString("Ruolo")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);

        }
        return null;
    }

    public void submitRequest(final String username, final String Descrizione, final String Tipologia) throws SQLException {
        String query = "INSERT INTO RICHIESTE (Username, CodiceRichiesta, Tipologia, Stato, Descrizione) VALUES (?, ?, ?, ?, ?)";
        int countA = 0;
        int countM = 0;
        int countR = 0;
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            switch (Tipologia) {
                case "Aggiunta":
                    countA++;
                    stmt.setString(2, "A" + countA + System.currentTimeMillis());
                    break;
                case "Modifica":
                    countM++;
                    stmt.setString(2, "M" + countM + System.currentTimeMillis());
                    break;
                case "Rimozione":
                    countR++;
                    stmt.setString(2, "R" + countR + System.currentTimeMillis());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid request type");
            }
            stmt.setString(3, Tipologia);
            stmt.setString(4, "Non visionata");
            stmt.setString(5, Descrizione);
            stmt.executeUpdate();
        }
    }

    public String[][] getRequestsStatus(final String username) {
        final String query = "SELECT CodiceRichiesta, Tipologia, Stato, Descrizione FROM RICHIESTE WHERE Username = ?";
        final List<String[]> requests = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(query) ) {
             stmt.setString(1, username);
             ResultSet rs = stmt.executeQuery();
             while (rs.next()) {
                 requests.add(new String[] {
                            rs.getString("CodiceRichiesta"),
                         rs.getString("Descrizione"),
                         rs.getString("Tipologia"),
                         rs.getString("Stato")
                 });
             }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return requests.toArray(new String[0][0]);
    }

    public void deleteRequest(final String codiceRichiesta) {
        final String query = "DELETE FROM RICHIESTE WHERE CodiceRichiesta = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, codiceRichiesta);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String[][] getRanking(final String tipoCompetizione, final String annoCalcistico){
        String query = "WITH MatchResults AS ( " +
                "    SELECT " +
                "        P.AnnoCalcistico,\n" +
                "        P.TipoCompetizione,\n" +
                "        P.CodiceCompetizione,\n" +
                "        P.CodicePartita,\n" +
                "        P.SquadraCasa,\n" +
                "        P.SquadraOspite,\n" +
                "        COALESCE(SUM(IF(SP.nomeSquadra = P.SquadraCasa, 1, 0)), 0) AS HomeGoals,\n" +
                "        COALESCE(SUM(IF(SP.nomeSquadra = P.SquadraOspite, 1, 0)), 0) AS AwayGoals\n" +
                "    FROM\n" +
                "        PARTITA P\n" +
                "    LEFT JOIN GOL G\n" +
                "        ON P.AnnoCalcistico = G.AnnoCalcistico\n" +
                "        AND P.CodiceCompetizione = G.CodiceCompetizione\n" +
                "        AND P.CodicePartita = G.CodicePartita\n" +
                "    LEFT JOIN STORICO_PARTECIPAZIONI SP\n" +
                "        ON G.CF_Marcatore = SP.CF_Calciatore\n" +
                "        AND G.AnnoCalcistico = SP.AnnoCalcistico\n" +
                "    WHERE " +
                "        P.AnnoCalcistico = ? " +
                "        AND P.TipoCompetizione = ?" +
                "    GROUP BY " +
                "        P.AnnoCalcistico, " +
                "        P.TipoCompetizione, " +
                "        P.CodiceCompetizione, " +
                "        P.CodicePartita, " +
                "        P.SquadraCasa, " +
                "        P.SquadraOspite " +
                "), " +
                "TeamResults AS ( " +
                "    SELECT " +
                "        AnnoCalcistico, " +
                "        TipoCompetizione, " +
                "        CodiceCompetizione, " +
                "        SquadraCasa AS Team, " +
                "        CASE " +
                "            WHEN HomeGoals > AwayGoals THEN 1 ELSE 0 " +
                "        END AS Wins, " +
                "        CASE " +
                "            WHEN HomeGoals < AwayGoals THEN 1 ELSE 0" +
                "        END AS Losses, " +
                "        CASE" +
                "            WHEN HomeGoals = AwayGoals THEN 1 ELSE 0 " +
                "        END AS Draws " +
                "    FROM " +
                "        MatchResults\n" +
                "    UNION ALL " +
                "    SELECT " +
                "        AnnoCalcistico, " +
                "        TipoCompetizione, " +
                "        CodiceCompetizione, " +
                "        SquadraOspite AS Team, " +
                "        CASE " +
                "            WHEN AwayGoals > HomeGoals THEN 1 ELSE 0 " +
                "        END AS Wins, " +
                "        CASE " +
                "            WHEN AwayGoals < HomeGoals THEN 1 ELSE 0 " +
                "        END AS Losses, " +
                "        CASE " +
                "            WHEN AwayGoals = HomeGoals THEN 1 ELSE 0 " +
                "        END AS Draws " +
                "    FROM " +
                "        MatchResults " +
                ") " +
                "SELECT " +
                "    T.Nome AS Nome, " +
                "    SUM(R.Wins) AS W, " +
                "    SUM(R.Losses) AS L, " +
                "    SUM(R.Draws) AS D, " +
                "    (SUM(R.Wins) * 3 + SUM(R.Draws)) AS Pt " +
                "FROM " +
                "    TeamResults R " +
                "JOIN SQUADRA T ON R.Team = T.Nome " +
                "GROUP BY " +
                "    T.Nome " +
                "ORDER BY " +
                "    Pt DESC ";


        final List<String[]> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, annoCalcistico);
            stmt.setString(2, tipoCompetizione);

            ResultSet rs = stmt.executeQuery();

            int i = 1;
            while (rs.next()) {
                results.add(new String[] {
                        i++ + "",
                        rs.getString("Nome"),
                        rs.getString("W"),
                        rs.getString("L"),
                        rs.getString("D"),
                        rs.getString("Pt")
                });
            }

        } catch (SQLException ignored) {
        }
        return results.toArray(new String[0][0]);

    }

    public String[][] mostPaidPlayers(){
        List<String[]> results = new ArrayList<>();
        final String query = "SELECT  nome, Cognome, valore, NomeSquadra " +
                "from CALCIATORE C, CONTRATTO CON , storico_partecipazioni st " +
                "where C.CF = CON.CF_Calciatore " +
                "and C.CF = st.CF_Calciatore " +
                "order by valore desc " +
                "limit 10";

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            int i = 1;
            while (rs.next()) {
                results.add(new String[] {
                        i++ + "",
                        rs.getString("nome"),
                        rs.getString("valore")
                });
            }
            return results.toArray(new String[0][0]);
        } catch (SQLException ignored) {

        }
        return results.toArray(new String[0][0]);
    }


    // 4. Aggiunta di un nuovo calciatore
//    public void addPlayer(String nome, String cognome, String cf, Date dataNascita, int altezza, String luogoNascita, String piedePreferito) throws SQLException {
//        //String query = "INSERT INTO CALCIATORE (Nome, Cognome, CF, Data_di_nascita, Altezza, Luogo_di_nascita, Piede_preferito) VALUES (?, ?, ?, ?, ?, ?, ?)";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, nome);
//            stmt.setString(2, cognome);
//            stmt.setString(3, cf);
//            stmt.setDate(4, dataNascita);
//            stmt.setInt(5, altezza);
//            stmt.setString(6, luogoNascita);
//            stmt.setString(7, piedePreferito);
//            stmt.executeUpdate();
//        }
//    }
//
//    // 5. Invio richieste da parte degli utenti
//    public void sendRequest(final String username,
//                            final String codiceRichiesta,
//                            final String Tipologia,
//                            final String Descrizione) throws SQLException {
//        String query = "INSERT INTO RICHIESTE (Username, CodiceRichiesta,Tipologia, Stato, Descrizione) VALUES (?, ?, ?, ?, ?)";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, username);
//            stmt.setString(2, codiceRichiesta);
//            stmt.setString(3, Tipologia);
//            stmt.setString(4, "In attesa");
//            stmt.setString(5, Descrizione);
//            stmt.executeUpdate();
//        }
//    }
//
//    // 6. Rimozione richieste già visionate da parte dell’amministratore
//    public void removeViewedRequests() throws SQLException {
//        String query = "DELETE FROM RICHIESTE WHERE Stato = 'Visionata'";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.executeUpdate();
//        }
//    }
//
//    // 7. Modifica dell’amministratore di una statistica di un calciatore in una stagione
//    public void updatePlayerStats(String cf, int gol, int assist, String annoCalcistico) throws SQLException {
//        String query = "UPDATE stats_giocatore_stagione SET Goal_stagionali = ?, Assist_stagionali = ? WHERE CF_Calciatore = ? AND AnnoCalcistico = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setInt(1, gol);
//            stmt.setInt(2, assist);
//            stmt.setString(3, cf);
//            stmt.setString(4, annoCalcistico);
//            stmt.executeUpdate();
//        }
//    }
//
//    // 8. Visualizzazione delle statistiche complessive della carriera di un calciatore
//    public void viewCareerStats(String cf) throws SQLException {
//        String query = "SELECT SUM(Goal_stagionali) AS GolTotali, SUM(Goal_stagionali) AS AssistTotali FROM stats_giocatore_stagione WHERE CF_Calciatore = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, cf);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    System.out.println("GolTotali: " + rs.getInt("GolTotali"));
//                    System.out.println("AssistTotali: " + rs.getInt("AssistTotali"));
//                }
//            }
//        }
//    }
//
//    // 9. Modifica dell’amministratore di una statistica di una squadra
//    public void updateTeamStats(final String nomeSquadra, int nomeStatistica, String annoCalcistico) throws SQLException {
//        String query = "UPDATE squadra SET Record_goal WHERE  password()= ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setInt(1, nomeStatistica);
//            stmt.setString(2, nomeSquadra);
//            stmt.setString(3, annoCalcistico);
//            stmt.executeUpdate();
//        }
//    }
//
//    // 10. Assegnazione di una targhetta da parte di un amministratore ad un utente
//    public void assignBadge(String username, String tipoTarghetta) throws SQLException {
//        String query = "ALTER TABLE utente ADD COLUMN tipoTarghetta VARCHAR(255)";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, username);
//            stmt.setString(2, tipoTarghetta);
//            stmt.executeUpdate();
//        }
//    }
//
//    // 11. Visualizzazione delle statistiche di un calciatore in una determinata stagione
//    public void viewPlayerStatsInSeason(String cf, String annoCalcistico) throws SQLException {
//        String query = "SELECT * FROM stats_giocatore_stagione WHERE CF_Calciatore = ? AND AnnoCalcistico = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, cf);
//            stmt.setString(2, annoCalcistico);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the statistics
//                }
//            }
//        }
//    }
//
//    // 12. Visualizzazione delle statistiche di un calciatore in una determinata competizione
//    public void viewPlayerStatsInCompetition(String cf, String codiceCompetizione) throws SQLException {
//        String query = "SELECT * FROM STATISTICHE WHERE CF_Calciatore = ? AND CodiceCompetizione = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, cf);
//            stmt.setString(2, codiceCompetizione);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the statistics
//                }
//            }
//        }
//    }
//
//    // 13. Visualizzazione classifica giocatori con valore di mercato più alto in una determinata stagione
//    public void viewTopPlayersByMarketValue(String annoCalcistico) throws SQLException {
//        String query = "SELECT CF_Calciatore, ValoreMercato FROM STATISTICHE WHERE AnnoCalcistico = ? ORDER BY ValoreMercato DESC LIMIT 10";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, annoCalcistico);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the player information
//                }
//            }
//        }
//    }
//
//    // 14. Visualizzazione della classifica finale di una competizione in una determinata stagione
//    public void viewFinalCompetitionRanking(String codiceCompetizione, String annoCalcistico) throws SQLException {
//        String query = "SELECT NomeSquadra, Posizione FROM CLASSIFICHE WHERE CodiceCompetizione = ? AND AnnoCalcistico = ? ORDER BY Posizione ASC LIMIT 4";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, codiceCompetizione);
//            stmt.setString(2, annoCalcistico);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the ranking information
//                }
//            }
//        }
//    }
//
//    // 15. Visualizzazione migliori giocatori della stagione (capocannoniere, assistman, etc.)
//    public void viewTopPlayersOfSeason(String annoCalcistico) throws SQLException {
//        String topScorerQuery = "SELECT CF_Calciatore, Gol FROM STATISTICHE WHERE AnnoCalcistico = ? ORDER BY Gol DESC LIMIT 1";
//        String topAssistmanQuery = "SELECT CF_Calciatore, Assist FROM STATISTICHE WHERE AnnoCalcistico = ? ORDER BY Assist DESC LIMIT 1";
//
//        try (PreparedStatement scorerStmt = connection.prepareStatement(topScorerQuery);
//             PreparedStatement assistmanStmt = connection.prepareStatement(topAssistmanQuery)) {
//            scorerStmt.setString(1, annoCalcistico);
//            assistmanStmt.setString(1, annoCalcistico);
//
//            try (ResultSet rs = scorerStmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the top scorer
//                }
//            }
//
//            try (ResultSet rs = assistmanStmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the top assistman
//                }
//            }
//        }
//    }
//
//    // 16. Visualizzazione classifica dei calciatori più pagati
//    public void viewHighestPaidPlayers() throws SQLException {
//        String query = "SELECT CF_Calciatore, Stipendio FROM CONTRATTI ORDER BY Stipendio DESC LIMIT 10";
//        try (PreparedStatement stmt = connection.prepareStatement(query);
//             ResultSet rs = stmt.executeQuery()) {
//            while (rs.next()) {
//                // Output the highest paid players
//            }
//        }
//    }
//
//    // 17. Visualizzazione esito e statistiche di una partita in una determinata stagione e competizione
//    public void viewMatchOutcomeAndStats(String codiceCompetizione, String annoCalcistico, String dataPartita) throws SQLException {
//        String query = "SELECT * FROM PARTITE WHERE CodiceCompetizione = ? AND AnnoCalcistico = ? AND DataPartita = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, codiceCompetizione);
//            stmt.setString(2, annoCalcistico);
//            stmt.setString(3, dataPartita);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the match information
//                }
//            }
//        }
//    }
//
//    // 18. Visualizzazione dei calciatori svincolati o non più in attività
//    public void viewFreeAgentsOrRetiredPlayers() throws SQLException {
//        String query = "SELECT Nome, Cognome, CF FROM CALCIATORE WHERE Status = 'Svincolato' OR Status = 'Ritirato'";
//        try (PreparedStatement stmt = connection.prepareStatement(query);
//             ResultSet rs = stmt.executeQuery()) {
//            while (rs.next()) {
//                // Output the player information
//            }
//        }
//    }
//
//    // 19. Visualizzazione informazioni di una competizione
//    public void viewCompetitionInfo(String codiceCompetizione) throws SQLException {
//        String query = "SELECT * FROM COMPETIZIONE WHERE CodiceCompetizione = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, codiceCompetizione);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the competition information
//                }
//            }
//        }
//    }
//
//    // 20. Visualizzazione top 5 marcatori e assistman in un'edizione di una competizione
//    public void viewTop5ScorersAndAssistmen(String codiceCompetizione) throws SQLException {
//        String topScorersQuery = "SELECT CF_Calciatore, Gol FROM STATISTICHE WHERE CodiceCompetizione = ? ORDER BY Gol DESC LIMIT 5";
//        String topAssistmenQuery = "SELECT CF_Calciatore, Assist FROM STATISTICHE WHERE CodiceCompetizione = ? ORDER BY Assist DESC LIMIT 5";
//
//        try (PreparedStatement scorerStmt = connection.prepareStatement(topScorersQuery);
//             PreparedStatement assistmanStmt = connection.prepareStatement(topAssistmenQuery)) {
//            scorerStmt.setString(1, codiceCompetizione);
//            assistmanStmt.setString(1, codiceCompetizione);
//
//            try (ResultSet rs = scorerStmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the top 5 scorers
//                }
//            }
//
//            try (ResultSet rs = assistmanStmt.executeQuery()) {
//                while (rs.next()) {
//                    // Output the top 5 assistmen
//                }
//            }
//        }
//    }
}


