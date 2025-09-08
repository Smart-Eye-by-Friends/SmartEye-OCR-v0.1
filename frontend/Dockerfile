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

# 빌드된 React 앱을 Nginx 기본 디렉토리로 복사
COPY --from=build /app/build /usr/share/nginx/html

# 80 포트 노출
EXPOSE 80

# Nginx 시작 (기본 명령어)
CMD ["nginx", "-g", "daemon off;"]