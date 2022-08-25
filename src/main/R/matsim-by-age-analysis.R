library(tidyverse)
library(sf)

savePlotAsJpg <- function(plot = last_plot(), name){
  svn = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/data/SrV_2018/Plots"
  date = Sys.Date()
  filepath = paste0(svn, "/", date, "-MATSim-", name, ".jpg")
  ggsave(plot = plot, filename = filepath)
}

#### DATA IMPORT ####
TRIPS = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-flexa-25pct-scaledFleet-baseCase_noDepot.output_trips.csv.gz"
PERSONS = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-flexa-25pct-scaledFleet-baseCase_noDepot.output_persons.csv.gz"

trips.raw = read.csv2(TRIPS, sep = ";")
persons = read.csv2(PERSONS)

join = trips.raw %>%
  select(person, trip_id, traveled_distance, main_mode, start_activity_type, end_activity_type, ends_with("_x"), ends_with("_y")) %>%
  left_join(persons, by = "person") %>%
  filter(!is.na(trip_id))

no.trips = trips.raw %>%
  anti_join(persons, by = "person") %>%
  nrow()

print(paste(no.trips, "agents stayed home all day ..."))
rm(trips.raw, persons, TRIPS, PERSONS, no.trips)

#### FILTER BY SHAPEFILE ####

shp = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/leipzig-utm32n/leipzig-utm32n.shp"

sf = st_read(shp)

# inspect only trips within the shapefile
filter = join %>%
  st_as_sf(coords = c("start_x", "start_y"), crs = 25832) %>%
  st_filter(sf) %>%
  st_as_sf(coords = "end_x", "end_y", crs = 25832) %>%
  st_filter(sf)

trips = filter %>%
  select(-c(subpopulation, starts_with("first_"), good_type, executed_score, end_x, end_y))


rm(shp, filter)


#### Analysis of mode share by age etc. ####

age.labels = c("Keine Angabe", "< 18", "18 - 24", "25 - 34", "35 - 50", "51 - 64", "> 65")
age.breaks = c(-Inf, 0, 18, 25, 35, 51, 65, Inf)

trips.1 = trips %>%
  mutate(age_bin = cut(age, age.breaks, age.labels, right = F),
         mode_fct = factor(main_mode, levels = c("walk", "bike", "car", "ride", "pt", "drtNorth", "drtSoutheast" )))

trips.agg.1 = trips.1 %>%
  filter(!mode_fct %in% c("drtNorth", "drtSoutheast")) %>%
  group_by(age_bin, mode_fct) %>%
  summarise(n = n()) %>%
  mutate(share = n / sum(n))

ggplot(trips.agg.1, aes(age_bin, share, fill = mode_fct)) +
  
  geom_col(position = "dodge", color = "black") +
  
  labs(x = "Age", y = "Modal share", fill = "Main mode") +
  
  theme_bw() +
  
  theme(legend.position = "bottom")

savePlotAsJpg(name = "Modal_Share_by_age_column")

#### Age distribution on mode ride ####

ride = trips.1 %>%
  filter(main_mode == "ride") %>%
  group_by(age_bin) %>%
  summarise(n = n()) %>%
  mutate(share = n/ sum(n))

ggplot(ride, aes(age_bin, share)) +
  
  geom_col(fill = "darkorange") +
  
  labs(x = "Age", y = "Share of total ride trips") +
  
  theme_bw()


#### Number of trips per day and person ####

trips.agg.2 = trips.1 %>%
  select(person, main_mode, age, sex) %>%
  group_by(person, main_mode) %>%
  summarise(age = first(age),
            sex = first(sex),
            n = n()) %>%
  ungroup() %>%
  group_by(person) %>%
  mutate(total = sum(n),
         src = "MATSim")


ggplot(trips.agg.2, aes(age, total, color = sex)) +
  
  geom_smooth() +
  
  coord_cartesian(ylim = c(2, 5)) +
  
  labs(x = "Age", y = "Trips per day and person", color = "Sex") +
  
  theme_bw()

## Load SrV Data to compare ##
SRV = "C:/Users/ACER/Desktop/Uni/VSP/matsim-leipzig/output/srv_join.csv"

srv.0 = read.csv(SRV)

matsim.labels = c("none", "walk", "bike", "car", "ride", "pt", "other")
matsim.breaks = c(-Inf, 1, 2, 3, 7, 10, 18, Inf)

srv.1 = srv.0 %>%
  mutate(matsim_mode = cut(V_VM_LAENG, matsim.breaks, matsim.labels, right = F)) %>%
  group_by(key, matsim_mode) %>%
  summarise(n_unweight = n(),
            V_GESCHLECHT = first(V_GESCHLECHT),
            V_ALTER = first(V_ALTER),
            GEWICHT_P = first(GEWICHT_P)) %>%
  mutate(n_weight = n_unweight * GEWICHT_P) %>%
  ungroup() %>%
  group_by(key) %>%
  mutate(total_weight = sum(n_weight)) %>%
  ungroup() %>%
  filter(!matsim_mode %in% c("none", "other")) %>%
  transmute(person = key, sex = ifelse(V_GESCHLECHT == 1, "m", "w"), age = V_ALTER, 
            main_mode = matsim_mode, n = n_weight, total = total_weight, src = "SrV")

compare = bind_rows(srv.1, trips.agg.2)

ggplot(compare, aes(age, total, color = src)) +
  
  geom_smooth() +
  
  labs(x = "Age", y = "Total Trips per day and person", color = "Source") +
  
  theme_bw()

savePlotAsJpg(name = "SrV_Smooth_trips_compare")

ggplot(trips.agg.2, aes(age, total, color = sex)) +
  
  geom_smooth()  +
  
  labs(x = "Age", y = "Total Trips per day and person", color = "Sex") +
  
  theme_bw()
  
savePlotAsJpg(name = "Smooth_trips_sex_impact")
