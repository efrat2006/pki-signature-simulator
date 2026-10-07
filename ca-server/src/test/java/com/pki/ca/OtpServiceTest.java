//package com.pki.ca;
//
//import com.pki.ca.repositories.OTPTokenRepo;
//import com.pki.ca.services.OTPTokenService;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.when;
//
//
//@SpringBootTest
//class OtpServiceTest {
//
//    @MockBean
//    private OTPTokenRepo otpRepository;
//
//    @Autowired
//    private OTPTokenService otpService;
//
//    @Test
//    void testGenerateAndSendOtp() {
//        Mockito.when(otpRepository.save(any())).thenReturn(null);
//
//        try {
//            // הקריאה הזאת שולחת מייל באמת
//            String result = otpService.createAndSaveOTP("efrat.mani123@gmail.com");
//
//            assertNotNull(result);
//            System.out.println("Check your inbox! OTP sent: " + result);
//        } catch(Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//}