library(tidyverse)

savePlotAsJpg <- function(plot = last_plot(), name){
  svn = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/data/SrV_2018/Plots"
  date = Sys.Date()
  filepath = paste0(svn, "/", date, "-", name, ".jpg")
  ggsave(plot = plot, filename = filepath)
}


SRV.H = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/data/SrV_2018/SrV2018_Einzeldaten_Leipzig_LE_SciUse_H2018.csv"
SRV.W = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/data/SrV_2018/SrV2018_Einzeldaten_Leipzig_LE_SciUse_W2018.csv"
SRV.P = "C:/Users/ACER/Desktop/Uni/VSP/NaMAV/data/SrV_2018/SrV2018_Einzeldaten_Leipzig_LE_SciUse_P2018.csv"

srv.h.raw = read.csv2(SRV.H)
srv.p.raw = read.csv2(SRV.P)
srv.w.raw = read.csv2(SRV.W)

# About data frame names: 'p' -> "Person"
#                         'h' -> "Haushalt"
#                         'w' -> "Wege"

srv.w.1 = srv.w.raw %>%
  select(HHNR, PNR, GEWICHT_W, V_VM_LAENG, E_HVM, E_HVM_4) %>%
  mutate(key = paste0(HHNR, "-", PNR))

srv.p.1 = srv.p.raw %>%
  select(HHNR, PNR, V_ALTER, E_ALTER_5, GEWICHT_P) %>%
  mutate(key = paste0(HHNR, "-", PNR))

no.match = anti_join(srv.p.1, srv.w.1, by = "key") %>% nrow()
print(paste(no.match, "rows can't be matched"))

srv.0 = srv.p.1 %>% 
  left_join(srv.w.1, by = "key") %>%
  filter(!is.na(HHNR.y)) %>%
  select(-ends_with(".y")) %>%
  rename("HHNR" = "HHNR.x",
         "PNR" = "PNR.x")

mode.levels = c("1", "2", "18", "19", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "70", "-10")

mode.labels = c("Zu Fuß", "Fahrrad", "Elektrofahrrad", "Leihfahrrad", "Moped/Motorrad/Motorroller",
           "Pkw als Fahrer im Haushalts-Pkw", "Pkw als Fahrer im Carsharing-Pkw", "Pkw als Fahrer im anderen Pkw",
           "Pkw als Mitfahrer im Haushalts-Pkw", "Pkw als Mitfahrer im Carsharing-Pkw",
           "Pkw als Mitfahrer im anderen Pkw", "Bus", "Straßenbahn/Tram", "U-Bahn", "S-Bahn",
           "Nahverkehrszug", "Fernverkehrszug", "Taxi", "Fernbus", "Anderes Verkehrsmittel", "Unplausibel")

matsim.labels = c("none", "walk", "bike", "car", "ride", "pt", "other")
matsim.breaks = c(-Inf, 1, 2, 3, 7, 10, 18, Inf)

age.labels = c("Keine Angabe", "< 18", "19 - 24", "25 - 34", "35 - 50", "51 - 64", "> 65")
age.breaks = c(-Inf, 0, 18, 25, 35, 51, 65, Inf)

srv.agg.1 = srv.0 %>%
  group_by(key, V_VM_LAENG) %>%
  summarise(
    age = first(V_ALTER),
    age_bin = first(E_ALTER_5),
    weight = first(GEWICHT_P),
    n_trips_unweight = n()
  ) %>%
  ungroup() %>%
  rename("person" = "key") %>%
  mutate(srv_mode = factor(as.character(V_VM_LAENG), levels = mode.levels, labels = mode.labels, ordered = F),
         V_VM_LAENG_1 = ifelse(V_VM_LAENG %in% c(18, 19), 2, V_VM_LAENG),
         matsim_mode = cut(V_VM_LAENG_1, breaks = matsim.breaks, labels = matsim.labels, right = F),
         n_trips_weight = weight * n_trips_unweight,
         age_labeled = cut(age, breaks = age.breaks, labels = age.labels)
        ) %>%
  select(-starts_with("V_VM"), n_trips_unweight)

srv.agg.2 = srv.agg.1 %>%
  group_by(age_labeled, matsim_mode) %>%
  summarise(mean_trips = mean(n_trips_weight),
            median_trips = median(n_trips_weight),
            n = n()) %>%
  ungroup() %>%
  group_by(age_labeled) %>%
  mutate(sum = sum(n),
         share = n / sum) %>%
  select(-sum)

srv.agg.plot = srv.agg.2 %>%
  filter(matsim_mode != "none" & age_labeled != "Keine Angabe") %>%
  mutate(share_percent = share * 100,
         mode_fct = factor(matsim_mode, levels = c("walk", "bike", "pt", "ride", "car", "other", "none")))

rm(srv.p.1, srv.w.1, srv.h.raw, srv.p.raw, srv.w.raw)

srv.agg.1 %>%
  filter(age <= 18 & matsim_mode == "car")

######## PLOTS ########

ggplot(srv.agg.plot, aes(mode_fct, share_percent, fill = mode_fct)) +
  
  geom_col() +
  
  scale_y_continuous(breaks = seq(0, 50, 5)) +
  
  labs(x = "Mode",
       y = "Modal share in percentage") +
  
  facet_wrap(. ~ age_labeled) +
  
  theme_bw() +
  
  theme(legend.position = "none")

savePlotAsJpg(name = "Modal_Share_by_Age")

ggplot(filter(srv.agg.plot, mode_fct == "ride"), aes(age_labeled, median_trips, fill = share)) +
  
  geom_col() +
  
  labs(x = "Age", y = "Median ride trips per person and day", fill = "Share of\nmode 'ride'") +
  
  theme_bw()

savePlotAsJpg(name = "Median_ride_trips")

ggplot(filter(srv.agg.plot, n > 10), aes(x = mode_fct, y = mean_trips, fill = mode_fct)) +
  
  geom_col() +
  
  labs(x = "Mode", y = "Mean trips per day and person") +
  
  facet_wrap(. ~ age_labeled) +
  
  theme_bw() +
  
  theme(legend.position = "none")

srv.cor = srv.agg.1 %>%
  group_by(age, matsim_mode) %>%
  summarise(n = n()) %>%
  ungroup() %>%
  group_by(age) %>%
  mutate(sum = sum(n),
         share = n / sum) %>%
  select(-sum) %>%
  filter(matsim_mode == "ride")

#Non linear regression analysis of age and ride share
model = lm(formula = share ~ I(age^3) + I(age^2) + age, data = filter(srv.cor, age > 2))
new = data.frame(age = srv.cor$age)
predict = predict(model, new, interval = "prediction") %>% 
  as.data.frame()

srv.cor.2 = cbind(srv.cor, predict$fit) %>%
  rename("predict" = "...5")

ggplot(srv.cor.2, aes(age, share)) +
  
  geom_point() +
  
  geom_line(aes(age, predict), size = 1.25, color = "red") +
  
  scale_x_continuous(breaks = seq(0, 100, 10)) +
  
  labs(y = "Share of ride trips") +
  
  theme_bw()

savePlotAsJpg(name = "Regression_Age_Ride_Share")

rm(srv.cor.2, predict, model, new)

ggplot(filter(srv.agg.plot, matsim_mode != "other"), aes(age_labeled, share, fill = matsim_mode)) +
  
  geom_col(position = position_dodge(), color = "black") +
  
  scale_y_continuous(breaks = seq(0, 0.5, 0.05)) +
  
  labs(y = "Modal Share by age", x = "Age in years", fill = "Mode") +
  
  theme_bw() +
  
  theme(legend.position = "bottom")

savePlotAsJpg(name = "Modal_Share_by_age_column")