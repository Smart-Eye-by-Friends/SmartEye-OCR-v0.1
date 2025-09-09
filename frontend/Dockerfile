# 멀티 스테이지 빌드 - Frontend Docker 이미지
FROM node:18-alpine as build

# 작업 디렉토리 설정
WORKDIR /app

# package.json과 package-lock.json 복사 (의존성 캐싱 최적화)
COPY package*.json ./

# 의존성 설치 (프로덕션 모드)
RUN npm ci --only=production

# 소스 코드 복사
COPY . .

# React 앱 빌드 (API URL을 Docker 환경용으로 설정)
ENV REACT_APP_API_URL=/api
RUN npm run build

# 프로덕션 스테이지 - Nginx로 정적 파일 서빙
FROM nginx:alpine

# non-root 사용자 생성 (nginx용)
RUN addgroup -g 1001 nginx-user && \
    adduser -D -u 1001 -G nginx-user nginx-user

# 빌드된 React 앱을 Nginx 기본 디렉토리로 복사
COPY --from=build /app/build /usr/share/nginx/html

# Nginx 설정 및 권한 설정
RUN chown -R nginx-user:nginx-user /usr/share/nginx/html && \
    chown -R nginx-user:nginx-user /var/cache/nginx && \
    chown -R nginx-user:nginx-user /var/log/nginx && \
    chown -R nginx-user:nginx-user /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown -R nginx-user:nginx-user /var/run/nginx.pid

# 80 포트 노출
EXPOSE 80

# non-root 사용자로 전환
USER nginx-user

# Nginx 시작 (기본 명령어)
CMD ["nginx", "-g", "daemon off;"]