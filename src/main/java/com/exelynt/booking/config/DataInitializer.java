package com.exelynt.booking.config;

import com.exelynt.booking.entity.*;
import com.exelynt.booking.repository.ReservationRepository;
import com.exelynt.booking.repository.ResourceRepository;
import com.exelynt.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final ReservationRepository reservationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           ResourceRepository resourceRepository,
                           ReservationRepository reservationRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.reservationRepository = reservationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            logger.info("Seeding initial users, resources, and reservations...");

            // 1. Seed Users
            User admin = new User(
                    "admin",
                    "admin@exelynt.com",
                    passwordEncoder.encode("admin123"),
                    Role.ROLE_ADMIN
            );
            userRepository.save(admin);

            User user = new User(
                    "user",
                    "user@exelynt.com",
                    passwordEncoder.encode("user123"),
                    Role.ROLE_USER
            );
            userRepository.save(user);

            User john = new User(
                    "john_doe",
                    "john@exelynt.com",
                    passwordEncoder.encode("password123"),
                    Role.ROLE_USER
            );
            userRepository.save(john);

            logger.info("Seed users created: admin (ROLE_ADMIN), user (ROLE_USER), john_doe (ROLE_USER)");

            // 2. Seed Resources
            Resource boardroom = new Resource(
                    "Executive Boardroom",
                    ResourceType.ROOM,
                    "Equipped with 4K video conferencing system, whiteboard, and high-speed fiber.",
                    "HQ Tower - 4th Floor, Suite 401",
                    16,
                    new BigDecimal("50.00"),
                    true
            );
            resourceRepository.save(boardroom);

            Resource tesla = new Resource(
                    "Tesla Model 3",
                    ResourceType.VEHICLE,
                    "All-electric premium sedan for client visits and corporate transit.",
                    "HQ Underground Parking Bay B-12",
                    5,
                    new BigDecimal("75.00"),
                    true
            );
            resourceRepository.save(tesla);

            Resource camera = new Resource(
                    "Sony FX6 Cinema Camera Kit",
                    ResourceType.EQUIPMENT,
                    "Full-frame 4K cinema kit with GM lenses, wireless audio, and dual tripods.",
                    "Media Locker #3",
                    1,
                    new BigDecimal("120.00"),
                    true
            );
            resourceRepository.save(camera);

            Resource auditorium = new Resource(
                    "Grand Auditorium Hall",
                    ResourceType.ROOM,
                    "High-capacity hall with professional acoustics, lighting, and dual laser projectors.",
                    "Campus Center - West Wing",
                    250,
                    new BigDecimal("250.00"),
                    true
            );
            resourceRepository.save(auditorium);

            logger.info("Seed resources created (Boardroom, Tesla Model 3, Cinema Camera, Auditorium)");

            // 3. Seed Reservations
            LocalDateTime now = LocalDateTime.now();

            Reservation res1 = new Reservation(
                    user,
                    boardroom,
                    now.plusDays(1).withHour(10).withMinute(0).withSecond(0),
                    now.plusDays(1).withHour(12).withMinute(0).withSecond(0),
                    ReservationStatus.CONFIRMED,
                    new BigDecimal("100.00"),
                    "Quarterly sprint review meeting"
            );
            reservationRepository.save(res1);

            Reservation res2 = new Reservation(
                    user,
                    tesla,
                    now.plusDays(2).withHour(9).withMinute(0).withSecond(0),
                    now.plusDays(2).withHour(17).withMinute(0).withSecond(0),
                    ReservationStatus.PENDING,
                    new BigDecimal("600.00"),
                    "Client visit travel"
            );
            reservationRepository.save(res2);

            Reservation res3 = new Reservation(
                    john,
                    camera,
                    now.plusDays(3).withHour(14).withMinute(0).withSecond(0),
                    now.plusDays(3).withHour(18).withMinute(0).withSecond(0),
                    ReservationStatus.CONFIRMED,
                    new BigDecimal("480.00"),
                    "Corporate promo video shoot"
            );
            reservationRepository.save(res3);

            Reservation res4 = new Reservation(
                    john,
                    boardroom,
                    now.plusDays(4).withHour(15).withMinute(0).withSecond(0),
                    now.plusDays(4).withHour(16).withMinute(0).withSecond(0),
                    ReservationStatus.CANCELLED,
                    new BigDecimal("50.00"),
                    "Cancelled team sync"
            );
            reservationRepository.save(res4);

            logger.info("Seed reservations created successfully.");
        }
    }
}
