export const environment = {
  production: false,
  api: {
    directory: {
      url: 'http://localhost:8081',
      endpoints: {
        login: '/login',
        logout: '/signout',
        refreshToken: '/refresh-token',
        registerUser: '/register-user',
        registerGhostUser: '/register-ghost-user',
        current: '/current',
        user: '/users',
      }
    },
    product: {
      url: 'http://localhost:8082',
      endpoints: {
        category: '/categories',
        package: '/packages',
        product: '/products',
        order: '/orders',
      }
    },
    viaCep: {
      url: 'https://viacep.com.br/ws',
    },
  }
};
