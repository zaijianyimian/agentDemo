package com.example.demo.service.auth;

import com.example.demo.dto.auth.PuzzleCaptchaResponse;
import com.example.demo.dto.auth.PuzzleCaptchaVerifyResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PuzzleCaptchaService {

    private static final int BOARD_WIDTH = 320;
    private static final int BOARD_HEIGHT = 160;
    private static final int PIECE_SIZE = 44;
    private static final int CAPTCHA_EXPIRE_SECONDS = 180;
    private static final int TICKET_EXPIRE_SECONDS = 180;
    private static final double PASS_TOLERANCE = 2.2;

    private final Map<String, CaptchaChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, CaptchaTicket> tickets = new ConcurrentHashMap<>();

    public PuzzleCaptchaResponse createPuzzleCaptcha() {
        clearExpired();

        long seed = System.nanoTime();
        Random random = new Random(seed);
        int targetX = 80 + random.nextInt(BOARD_WIDTH - 160);
        int targetY = 40 + random.nextInt(BOARD_HEIGHT - 80);
        double targetPercent = Math.round((targetX * 10000.0 / BOARD_WIDTH)) / 100.0;
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(CAPTCHA_EXPIRE_SECONDS);
        String captchaId = UUID.randomUUID().toString().replace("-", "");

        String scene = buildScene(seed);
        String slotPath = puzzlePath(targetX, targetY, PIECE_SIZE);
        String localPiecePath = puzzlePath(PIECE_SIZE / 2.0, PIECE_SIZE / 2.0, PIECE_SIZE);

        String backgroundSvg = """
                <svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d' viewBox='0 0 %d %d'>
                  %s
                  <path d='%s' fill='rgba(12,18,33,0.24)' stroke='rgba(255,255,255,0.65)' stroke-width='1.8'/>
                </svg>
                """.formatted(BOARD_WIDTH, BOARD_HEIGHT, BOARD_WIDTH, BOARD_HEIGHT, scene, slotPath);

        int offsetX = targetX - PIECE_SIZE / 2;
        int offsetY = targetY - PIECE_SIZE / 2;
        String pieceSvg = """
                <svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d' viewBox='0 0 %d %d'>
                  <defs>
                    <clipPath id='pieceClip'>
                      <path d='%s'/>
                    </clipPath>
                  </defs>
                  <g clip-path='url(#pieceClip)'>
                    <g transform='translate(%d,%d)'>
                      %s
                    </g>
                  </g>
                  <path d='%s' fill='none' stroke='rgba(255,255,255,0.85)' stroke-width='1.5'/>
                </svg>
                """.formatted(PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, localPiecePath, -offsetX, -offsetY, scene, localPiecePath);

        challenges.put(captchaId, new CaptchaChallenge(captchaId, targetPercent, expiresAt));

        return PuzzleCaptchaResponse.builder()
                .captchaId(captchaId)
                .backgroundImage(toDataUrl(backgroundSvg))
                .pieceImage(toDataUrl(pieceSvg))
                .pieceWidth(PIECE_SIZE)
                .pieceHeight(PIECE_SIZE)
                .expiresInSeconds(CAPTCHA_EXPIRE_SECONDS)
                .build();
    }

    public PuzzleCaptchaVerifyResponse verifyPuzzleCaptcha(String captchaId, double sliderPercent) {
        clearExpired();
        CaptchaChallenge challenge = challenges.remove(captchaId);
        if (challenge == null || challenge.isExpired()) {
            return PuzzleCaptchaVerifyResponse.builder()
                    .passed(false)
                    .message("验证码已过期，请刷新后重试")
                    .build();
        }

        double delta = Math.abs(sliderPercent - challenge.targetPercent());
        if (delta > PASS_TOLERANCE) {
            return PuzzleCaptchaVerifyResponse.builder()
                    .passed(false)
                    .message("滑块未对齐，请重试")
                    .build();
        }

        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new CaptchaTicket(ticket, LocalDateTime.now().plusSeconds(TICKET_EXPIRE_SECONDS)));
        return PuzzleCaptchaVerifyResponse.builder()
                .passed(true)
                .ticket(ticket)
                .ticketExpiresInSeconds(TICKET_EXPIRE_SECONDS)
                .message("验证通过")
                .build();
    }

    public void consumeTicket(String ticket) {
        clearExpired();
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("请先完成图形验证");
        }
        CaptchaTicket captchaTicket = tickets.remove(ticket.trim());
        if (captchaTicket == null || captchaTicket.isExpired()) {
            throw new IllegalArgumentException("图形验证已失效，请重新验证");
        }
    }

    private void clearExpired() {
        LocalDateTime now = LocalDateTime.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String buildScene(long seed) {
        Random random = new Random(seed);
        StringBuilder builder = new StringBuilder();
        builder.append("<defs><linearGradient id='bg' x1='0' y1='0' x2='1' y2='1'>")
                .append("<stop offset='0%' stop-color='#fde68a'/>")
                .append("<stop offset='45%' stop-color='#fdba74'/>")
                .append("<stop offset='100%' stop-color='#fb923c'/>")
                .append("</linearGradient></defs>")
                .append("<rect width='100%' height='100%' fill='url(#bg)'/>");

        for (int i = 0; i < 14; i++) {
            int x = 20 + random.nextInt(BOARD_WIDTH - 40);
            int y = 14 + random.nextInt(BOARD_HEIGHT - 28);
            int radius = 5 + random.nextInt(20);
            int alpha = 10 + random.nextInt(22);
            builder.append("<circle cx='").append(x)
                    .append("' cy='").append(y)
                    .append("' r='").append(radius)
                    .append("' fill='rgba(255,255,255,0.").append(alpha).append(")'/>");
        }
        for (int i = 0; i < 6; i++) {
            int x1 = random.nextInt(BOARD_WIDTH);
            int y1 = random.nextInt(BOARD_HEIGHT);
            int x2 = random.nextInt(BOARD_WIDTH);
            int y2 = random.nextInt(BOARD_HEIGHT);
            builder.append("<line x1='").append(x1)
                    .append("' y1='").append(y1)
                    .append("' x2='").append(x2)
                    .append("' y2='").append(y2)
                    .append("' stroke='rgba(124,45,18,0.15)' stroke-width='1.2'/>");
        }
        return builder.toString();
    }

    private String puzzlePath(double centerX, double centerY, int size) {
        double half = size / 2.0;
        double notch = 8.0;
        double notchDepth = 7.0;
        double left = centerX - half;
        double top = centerY - half;
        double right = centerX + half;
        double bottom = centerY + half;
        double topNotchStart = centerX - notch;
        double topNotchEnd = centerX + notch;
        double rightNotchStart = centerY - notch;
        double rightNotchEnd = centerY + notch;
        return "M %.2f %.2f L %.2f %.2f L %.2f %.2f C %.2f %.2f %.2f %.2f %.2f %.2f L %.2f %.2f L %.2f %.2f C %.2f %.2f %.2f %.2f %.2f %.2f L %.2f %.2f L %.2f %.2f Z".formatted(
                left, top,
                topNotchStart, top,
                topNotchStart, top - notchDepth,
                centerX - 4, top - notchDepth - 4, centerX + 4, top - notchDepth - 4, topNotchEnd, top - notchDepth,
                topNotchEnd, top,
                right, top,
                right, rightNotchStart,
                right + notchDepth, centerY - 4, right + notchDepth, centerY + 4, right, rightNotchEnd,
                right, rightNotchEnd,
                right, bottom,
                left, bottom
        );
    }

    private String toDataUrl(String svg) {
        String base64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    private record CaptchaChallenge(String captchaId, double targetPercent, LocalDateTime expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(LocalDateTime.now());
        }
    }

    private record CaptchaTicket(String ticket, LocalDateTime expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(LocalDateTime.now());
        }
    }
}

