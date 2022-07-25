package analysis.emissions;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.matsim.testcases.MatsimTestUtils;

class RunOfflineAirPollutionAnalysisByVehicleCategoryTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils() ;

    @Test
    void run() {
        try {
            // TODO local file usage
            String runDirectory = "output/it-1pct/";
            String runId = "leipzig-25pct";
            String hbefaFileWarm = "https://svn.vsp.tu-berlin.de/repos/public-svn/3507bb3997e5657ab9da76dbedbb13c9b5991d3e/0e73947443d68f95202b71a156b337f7f71604ae/7eff8f308633df1b8ac4d06d05180dd0c5fdf577.enc";
            String hbefaFileCold = "https://svn.vsp.tu-berlin.de/repos/public-svn/3507bb3997e5657ab9da76dbedbb13c9b5991d3e/0e73947443d68f95202b71a156b337f7f71604ae/ColdStart_Vehcat_2020_Average_withHGVetc.csv.enc";
            String analysisOutputDirectory = "src/test/java/analysis/emissions/emission-analysis-offline-test";


            RunOfflineAirPollutionAnalysisByVehicleCategory analysis = new RunOfflineAirPollutionAnalysisByVehicleCategory(runDirectory, runId, hbefaFileWarm, hbefaFileCold, analysisOutputDirectory);
            analysis.run();

        } catch ( Exception ee ) {
            throw new RuntimeException(ee) ;
        }
    }
}